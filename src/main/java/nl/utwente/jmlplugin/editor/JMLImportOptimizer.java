package nl.utwente.jmlplugin.editor;

import com.intellij.lang.ImportOptimizer;
import com.intellij.lang.java.JavaImportOptimizer;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Optimize Imports for Java files that keeps the imports that are only used in JML comments: IntelliJ's own
 * optimizer does not look into comments, so it removed them. This runs IntelliJ's optimizer, and adds the imports
 * that JML uses back afterwards.
 */
public class JMLImportOptimizer implements ImportOptimizer {
    // the words in a JML comment that can be names; a backslash keyword such as \result is not one
    private static final Pattern NAME = Pattern.compile("(?<![\\w$\\\\])[A-Za-z_$][\\w$]*");

    private final ImportOptimizer javaOptimizer = new JavaImportOptimizer();

    @Override
    public boolean supports(@NotNull PsiFile file) {
        return file instanceof PsiJavaFile && javaOptimizer.supports(file);
    }

    @Override
    public @NotNull Runnable processFile(@NotNull PsiFile file) {
        Runnable optimize = javaOptimizer.processFile(file);
        List<PsiImportStatementBase> usedInJml = new ArrayList<>();
        for (PsiImportStatementBase statement : usedInJml((PsiJavaFile) file)) {
            usedInJml.add((PsiImportStatementBase) statement.copy());
        }
        if (usedInJml.isEmpty()) return optimize;
        Runnable restore = () -> restore((PsiJavaFile) file, usedInJml);
        if (optimize instanceof CollectingInfoRunnable collecting) {
            // keeps the "Removed n imports" message
            return new CollectingInfoRunnable() {
                @Override
                public void run() {
                    collecting.run();
                    restore.run();
                }

                @Override
                public @Nullable String getUserNotificationInfo() {
                    return collecting.getUserNotificationInfo();
                }
            };
        }
        return () -> {
            optimize.run();
            restore.run();
        };
    }

    /**
     * Adds the imports back that the Java optimizer removed
     */
    private static void restore(@NotNull PsiJavaFile file, @NotNull List<PsiImportStatementBase> usedInJml) {
        PsiImportList importList = file.getImportList();
        if (importList == null) return;
        Set<String> present = new HashSet<>();
        for (PsiImportStatementBase statement : importList.getAllImportStatements()) present.add(key(statement));
        for (PsiImportStatementBase statement : usedInJml) {
            if (present.add(key(statement))) importList.add(statement);
        }
    }

    /**
     * @return what an import imports, such as "static java.lang.Math.*"
     */
    private static @NotNull String key(@NotNull PsiImportStatementBase statement) {
        PsiJavaCodeReferenceElement reference = statement.getImportReference();
        String name = reference == null ? statement.getText() : reference.getQualifiedName();
        return (statement instanceof PsiImportStaticStatement ? "static " : "") + name + (statement.isOnDemand() ? ".*" : "");
    }

    /**
     * @param file a Java file
     * @return the imports of the file of which a name is used in its JML comments, whether or not Java code uses them too
     */
    public static @NotNull List<PsiImportStatementBase> usedInJml(@NotNull PsiJavaFile file) {
        PsiImportList importList = file.getImportList();
        if (importList == null || importList.getAllImportStatements().length == 0) return List.of();
        Set<String> names = jmlNames(file);
        if (names.isEmpty()) return List.of();
        List<PsiImportStatementBase> used = new ArrayList<>();
        for (PsiImportStatementBase statement : importList.getAllImportStatements()) {
            if (isUsed(statement, names, file)) used.add(statement);
        }
        return used;
    }

    /**
     * @return the words in the JML comments of the file that can be names
     */
    private static @NotNull Set<String> jmlNames(@NotNull PsiJavaFile file) {
        Set<String> names = new HashSet<>();
        for (PsiComment comment : PsiTreeUtil.findChildrenOfType(file, PsiComment.class)) {
            String text = comment.getText();
            if (!text.startsWith("//@") && !text.startsWith("/*@")) continue;
            Matcher matcher = NAME.matcher(text);
            while (matcher.find()) names.add(matcher.group());
        }
        return names;
    }

    private static boolean isUsed(@NotNull PsiImportStatementBase statement, @NotNull Set<String> names,
                                  @NotNull PsiJavaFile file) {
        PsiJavaCodeReferenceElement reference = statement.getImportReference();
        if (reference == null) return false;
        if (!statement.isOnDemand()) {
            // import java.util.List; or import static java.lang.Math.max;
            return names.contains(reference.getReferenceName());
        }
        PsiElement target = reference.resolve();
        if (statement instanceof PsiImportStaticStatement) {
            // import static java.lang.Math.*;
            if (!(target instanceof PsiClass psiClass)) return false;
            for (String name : names) {
                if (psiClass.findFieldByName(name, true) != null || psiClass.findMethodsByName(name, true).length > 0
                        || psiClass.findInnerClassByName(name, true) != null) {
                    return true;
                }
            }
            return false;
        }
        // import java.util.*;
        if (!(target instanceof PsiPackage psiPackage)) return false;
        for (String name : names) {
            if (psiPackage.findClassByShortName(name, file.getResolveScope()).length > 0) return true;
        }
        return false;
    }
}
