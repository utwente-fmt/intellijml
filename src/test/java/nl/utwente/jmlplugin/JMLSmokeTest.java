package nl.utwente.jmlplugin;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import nl.utwente.jmlplugin.annotator.JMLNames;
import nl.utwente.jmlplugin.editor.JMLImplicitUsageProvider;
import nl.utwente.jmlplugin.editor.JMLNameReference;
import nl.utwente.jmlplugin.psi.JMLFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Runs the editor features that work on all JML of a file over many files with JML, to find crashes and
 * inconsistencies: resolving every name in JML, Find Usages of what they refer to, the check for unused members, and
 * Reformat Code, which may only change whitespace, and gives the same text when run again. The files are those of the
 * tests, and with -PopenJml=&lt;installation&gt; the examples of OpenJML, and with -PjmlExamples=&lt;folder&gt; those of
 * a course:
 * ./gradlew test -PopenJml=&lt;installation&gt; -PjmlExamples=&lt;folder&gt; --tests "*JMLSmokeTest"
 */
public class JMLSmokeTest extends LightJavaCodeInsightFixtureTestCase {

    @Override
    protected @NotNull LightProjectDescriptor getProjectDescriptor() {
        return JAVA_21;
    }

    private final List<String> problems = new ArrayList<>();
    private int files;
    private int names;
    private int resolved;

    public void testFiles() throws IOException {
        List<Path> sources = new ArrayList<>(javaFiles(Path.of("src/test/testData")));
        String openJml = System.getProperty("openjml.path");
        if (openJml != null) {
            sources.addAll(javaFiles(Path.of(openJml, "tutorial")));
            sources.addAll(javaFiles(Path.of(openJml, "demos")));
        }
        String examples = System.getProperty("jml.examples");
        if (examples != null) sources.addAll(javaFiles(Path.of(examples)));
        for (Path source : sources) {
            String text;
            try {
                text = Files.readString(source);
            } catch (IOException e) {
                continue;
            }
            // the markup of highlighting tests
            text = text.replaceAll("</?(error|warning|weak_warning|info|EOLError|caret|selection)(\\s+descr=\"[^\"]*\")?\\s*/?>", "");
            if (!text.contains("//@") && !text.contains("/*@")) continue;
            try {
                check(source, text);
            } catch (Throwable e) {
                problems.add(source + ": " + e);
            }
        }
        System.out.println("SMOKE files " + files + " names " + names + " resolved " + resolved + " problems " + problems.size());
        assertTrue(String.join("\n", problems), problems.isEmpty());
    }

    private static List<Path> javaFiles(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) return List.of();
        try (Stream<Path> walk = Files.walk(folder)) {
            return walk.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }

    private void check(Path source, String text) {
        files++;
        PsiFile file = myFixture.configureByText(source.getFileName().toString(), text);
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(getProject());
        // the JML files, once each
        Set<PsiFile> jml = new LinkedHashSet<>();
        for (PsiComment comment : PsiTreeUtil.findChildrenOfType(file, PsiComment.class)) {
            if (comment instanceof PsiLanguageInjectionHost host) {
                manager.enumerate(host, (injected, places) -> {
                    if (injected instanceof JMLFile) jml.add(injected);
                });
            }
        }
        // what the names refer to, and how often
        Map<PsiElement, Integer> uses = new HashMap<>();
        for (PsiFile injected : jml) {
            for (PsiElement name : PsiTreeUtil.collectElements(injected, JMLNames::isName)) {
                names++;
                PsiElement target;
                try {
                    target = JMLNames.resolve(name);
                } catch (Throwable e) {
                    problems.add(source + ": resolving " + name.getText() + ": " + e);
                    continue;
                }
                if (target == null) continue;
                resolved++;
                if (target instanceof PsiNamedElement named && !name.getText().equals(named.getName())) {
                    problems.add(source + ": " + name.getText() + " resolves to " + named.getName());
                }
                // Find Usages only looks where Java (and spec_public) allows it, not in the JML of other classes for a
                // private method, which is an error there
                PsiElement host = manager.getInjectionHost(name);
                if (target.getContainingFile() == file && host != null
                        && PsiSearchScopeUtil.isInScope(PsiSearchHelper.getInstance(getProject()).getUseScope(target), host)) {
                    uses.merge(target, 1, Integer::sum);
                }
            }
        }
        // Find Usages finds as many uses in JML, and the unused check sees them
        JMLImplicitUsageProvider unused = new JMLImplicitUsageProvider();
        for (Map.Entry<PsiElement, Integer> use : uses.entrySet()) {
            PsiElement target = use.getKey();
            String what = source + ": " + ((PsiNamedElement) target).getName();
            Collection<PsiReference> references = target instanceof PsiMethod method
                    ? MethodReferencesSearch.search(method, GlobalSearchScope.fileScope(file), true).findAll()
                    : ReferencesSearch.search(target, GlobalSearchScope.fileScope(file)).findAll();
            long inJml = references.stream().filter(reference -> reference instanceof JMLNameReference).count();
            if (inJml != use.getValue()) {
                problems.add(what + ": " + use.getValue() + " uses in JML, Find Usages finds " + inJml);
            }
            if (!unused.isImplicitUsage(target)) problems.add(what + ": used in JML, but not for the unused check");
        }
        for (PsiElement member : PsiTreeUtil.findChildrenOfAnyType(file, PsiField.class, PsiMethod.class,
                PsiParameter.class, PsiLocalVariable.class)) {
            unused.isImplicitUsage(member);
            unused.isImplicitRead(member);
        }
        // Reformat Code only changes whitespace (and the * of Javadoc comments, as Java does), and again it changes
        // nothing
        String before = file.getText();
        reformat(file);
        String once = file.getText();
        if (!once.replaceAll("[\\s*]", "").equals(before.replaceAll("[\\s*]", ""))) {
            problems.add(source + ": Reformat Code changed more than whitespace: "
                    + firstDifference(before.replaceAll("\\s+", " "), once.replaceAll("\\s+", " ")));
        }
        reformat(file);
        if (!file.getText().equals(once)) problems.add(source + ": Reformat Code changes the text again: " + firstDifference(once, file.getText()));
    }

    private void reformat(PsiFile file) {
        WriteCommandAction.writeCommandAction(getProject()).run(() -> CodeStyleManager.getInstance(getProject()).reformat(file));
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    }

    private static String firstDifference(String a, String b) {
        int i = 0;
        while (i < a.length() && i < b.length() && a.charAt(i) == b.charAt(i)) i++;
        int start = Math.max(0, i - 40);
        return "[" + a.substring(start, Math.min(a.length(), i + 40)) + "] became [" + b.substring(start, Math.min(b.length(), i + 40)) + "]";
    }
}
