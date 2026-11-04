package nl.utwente.jmlplugin.annotator;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import nl.utwente.jmlplugin.psi.JMLClassInvariant;
import nl.utwente.jmlplugin.psi.JMLModifiers;
import nl.utwente.jmlplugin.psi.JMLTokenSet;
import nl.utwente.jmlplugin.psi.JMLTypes;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Util class for the annotators containing lookup functions for JML comments
 */
public class JMLAnnotatorUtil {
    public static final String ALL_MODIFIERS_REGEX;

    // make a regex string that can match any JML modifier; the longest first, so that non_null_by_default is not
    // matched as non_null
    static {
        ALL_MODIFIERS_REGEX = Arrays.stream(JMLTokenSet.allModifiers.getTypes()).map(IElementType::toString)
                .sorted(Comparator.comparingInt(String::length).reversed())
                .collect(Collectors.joining("|", "(", ")"));
    }

    // the keyword of a ghost declaration

    // whitespace with at most one line break
    private static final Pattern AT_MOST_ONE_NEWLINE = Pattern.compile("[ \t ]*(\n|\r\n)?[ \t ]*");

    // the patterns made by countTextMatchesAcrossComments and keywordPattern, as the annotators run for every element
    private static final Map<String, Pattern> PATTERNS = new ConcurrentHashMap<>();

    /**
     * @param whitespace the text of whitespace between two elements
     * @return true if the whitespace has at most one line break, so that the elements are on the same or adjacent lines
     */
    public static boolean isAtMostOneNewline(String whitespace) {
        return AT_MOST_ONE_NEWLINE.matcher(whitespace).matches();
    }

    /**
     * Gets the Java context element of the specification
     *
     * @param element Any JML PsiElement
     * @return the Java PSI context element (such as PsiClass or PsiMethod, etc.)
     */
    public static PsiElement getJavaContext(PsiElement element) {
        PsiComment commentContext = getCommentContext(element);
        return commentContext != null ? getOwnerIfModifierList(commentContext.getParent()) : null;
    }

    /**
     * A comment in between Java modifiers, such as in "public /*@ pure @*&#47; static int f()",
     * is part of the modifier list, but belongs to the declaration that owns the modifier list
     *
     * @param element the parent of a comment
     * @return the declaration owning the modifier list if the element is a modifier list, otherwise the element itself
     */
    public static PsiElement getOwnerIfModifierList(PsiElement element) {
        return element instanceof PsiModifierList ? element.getParent() : element;
    }

    /**
     * Gets the comment context element of the specification
     *
     * @param element JML PsiElement
     * @return the PsiComment context element
     */
    public static PsiComment getCommentContext(PsiElement element) {
        return PsiTreeUtil.getContextOfType(element, PsiComment.class);
    }

    /**
     * @param comment a JML comment
     * @return whether it belongs to the class rather than to the field or method below it: a ghost or model
     * declaration, an invariant, an initially clause, a constraint, a represents clause or an in clause, which OpenJML
     * also allows right above a field or method
     */
    public static boolean isClassLevelComment(@NotNull PsiComment comment) {
        return CLASS_LEVEL.matcher(jmlText(comment)).find();
    }

    // the keywords of a ghost or model declaration, invariant, initially clause, constraint, represents clause or in
    // clause, at the start of a clause, after modifiers; they can also be names elsewhere, as in "requires model != null;"
    private static final Pattern CLASS_LEVEL = Pattern.compile("(?:^/[/*]|[;@\\r\\n])[\\s@]*(?:" + ALL_MODIFIERS_REGEX
            + "\\s+)*(?:ghost|model|invariant|initially|constraint|represents|in)(?![\\w$])");

    /**
     * @param invariant an invariant, initially clause or constraint
     * @return its keyword: "invariant", "initially" or "constraint"
     */
    public static @NotNull String invariantKeyword(@NotNull JMLClassInvariant invariant) {
        for (PsiElement child = invariant.getFirstChild(); child != null; child = child.getNextSibling()) {
            IElementType type = child.getNode().getElementType();
            if (type == JMLTypes.INITIALLY || type == JMLTypes.CONSTRAINT || type == JMLTypes.INVARIANT) {
                return child.getText();
            }
        }
        return "invariant";
    }

    // a comment inside JML, which ends at the end of the line, or a string or character literal, which may contain //;
    // as in OpenJML, "//@" continues the JML, as in "//@ public model int side; //@ in size;"
    private static final Pattern LINE_COMMENT_OR_LITERAL =
            Pattern.compile("//(?!@)[^\\r\\n]*|\"(?:[^\"\\\\\\r\\n]|\\\\.)*\"|'(?:[^'\\\\\\r\\n]|\\\\.)*'");

    /**
     * @param comment a JML comment
     * @return the text of the comment, with the comments inside it replaced by spaces, such as "// why" in
     * "//@ assert x > 0; // why", so that searching the text does not find words in them
     */
    public static @NotNull String jmlText(@NotNull PsiComment comment) {
        String text = comment.getText();
        if (text.indexOf("//", 2) < 0) return text;
        StringBuilder result = new StringBuilder(text.substring(0, 2));
        Matcher matcher = LINE_COMMENT_OR_LITERAL.matcher(text).region(2, text.length());
        int last = 2;
        while (matcher.find()) {
            result.append(text, last, matcher.start());
            String match = matcher.group();
            result.append(match.startsWith("//") ? " ".repeat(match.length()) : match);
            last = matcher.end();
        }
        return result.append(text, last, text.length()).toString();
    }

    /**
     * Finds the loop that a loop specification, such as a loop invariant, belongs to: the loop statement right below
     * its comment, with only comments and at most one line break in between
     *
     * @param element an element in a JML comment in a method body
     * @return the loop below the comment, or null if there is none
     */
    public static PsiLoopStatement loopBelow(PsiElement element) {
        PsiComment commentContext = getCommentContext(element);
        if (commentContext == null) return null;
        PsiElement next = commentContext.getNextSibling();
        while (next instanceof PsiComment || (next instanceof PsiWhiteSpace && isAtMostOneNewline(next.getText()))) {
            next = next.getNextSibling();
        }
        return next instanceof PsiLoopStatement loop ? loop : null;
    }

    /**
     * Generates a list of all comments that come before the current comment
     *
     * @param myComment     the comment to look backwards from
     * @param alsoAddMyself whether myComment should be included in the resulting list
     * @return a list of all comments that come before the current comment, possible including myComment as well
     */
    public static ArrayList<PsiComment> collectPreviousComments(PsiComment myComment, boolean alsoAddMyself) {
        ArrayList<PsiComment> resultList = new ArrayList<>();
        if (myComment == null) return resultList;
        PsiElement parent = myComment.getParent();

        // get all comment backwards from currentComment, we do this always because all functions in the class want those for sure
        PsiElement currentElement = (alsoAddMyself ? myComment : myComment.getPrevSibling());
        while (true) {
            if (currentElement instanceof PsiComment) {
                resultList.add((PsiComment) currentElement);
            }
            // max 1 newline is allowed between comments for them to still belong to the same group,
            // unless they are a field or a method, then something other than whitespace is also allowed
            else if (currentElement == null || !(parent instanceof PsiField || parent instanceof PsiMethod || (currentElement instanceof PsiWhiteSpace
                    && isAtMostOneNewline(currentElement.getText())))) {
                // for local variables and parameters, it could be inside the declaration or before, if we are inside, also go before
                currentElement = null;
                if (parent instanceof PsiLocalVariable) {
                    currentElement = parent.getParent();
                    parent = null;
                } else if (parent instanceof PsiParameter) {
                    currentElement = parent;
                    parent = null;
                }
                // was not inside local variable or parameter
                if (currentElement == null) {
                    break;
                }
            }
            currentElement = currentElement.getPrevSibling();
        }
        return resultList;
    }

    /**
     * Checks whether the previous comments matches the regex
     *
     * @param element                the element from which to start the search
     * @param regex                  the regex that should be matched
     * @param doNotCountMyself       if true, at least 2 occurrences need to be found instead of 1
     * @param alsoSearchMyOwnComment if true, the current comment is included in the search
     * @return true if the regex could be matched in the previous comments
     */
    public static boolean doPrevCommentsContain(PsiElement element, String regex, boolean doNotCountMyself, boolean alsoSearchMyOwnComment) {
        PsiComment myComment = getCommentContext(element);
        ArrayList<PsiComment> comments = collectPreviousComments(myComment, alsoSearchMyOwnComment);
        return countTextMatchesAcrossComments(comments, regex, (doNotCountMyself ? 2 : 1)) >= (doNotCountMyself ? 2 : 1);
    }

    /**
     * Counts how often a keyword occurs in the specification case of an element: from the last "also" before it, in
     * its own comment or the comments before it, to the next "also" in its own comment
     *
     * @param element a JML element
     * @param keyword the keyword to count
     * @return the number of times the keyword occurs in the specification case
     */
    public static int countInSpecCase(@NotNull PsiElement element, String keyword) {
        PsiComment myComment = getCommentContext(element);
        if (myComment == null) return 0;
        ArrayList<PsiComment> previousComments = collectPreviousComments(myComment, false);
        Collections.reverse(previousComments);
        StringBuilder text = new StringBuilder();
        for (PsiComment comment : previousComments) {
            if (comment.getText().startsWith("/*@") || comment.getText().startsWith("//@")) {
                text.append(jmlText(comment)).append('\n');
            }
        }
        int elementOffset = text.length() - myComment.getTextRange().getStartOffset()
                + InjectedLanguageManager.getInstance(element.getProject()).injectedToHost(element, element.getTextOffset());
        text.append(jmlText(myComment));

        int start = 0;
        int end = text.length();
        Matcher also = keywordPattern("also").matcher(text);
        while (also.find()) {
            if (also.start() < elementOffset) {
                start = also.end();
            } else {
                end = also.start();
                break;
            }
        }
        Matcher matcher = keywordPattern(keyword).matcher(text).region(start, end);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /**
     * @param keyword a JML keyword
     * @return a pattern that finds the keyword as a whole word
     */
    private static Pattern keywordPattern(String keyword) {
        return PATTERNS.computeIfAbsent("keyword " + keyword,
                key -> Pattern.compile("(?<![\\w\\\\])" + Pattern.quote(keyword) + "(?!\\w)"));
    }

    /**
     * Checks whether the regex can be matched in comment containing an invariant
     *
     * @param element the element to search
     * @param tokens  the tokens out of which one needs to match with the token-type of the modifier
     * @return true if the regex could be matched in the comment
     */
    public static boolean doPrevInvariantModifiersContain(PsiElement element, TokenSet tokens) {
        if (element == null) return false;
        for (PsiElement currentElement = element.getPrevSibling(); currentElement != null; currentElement = currentElement.getPrevSibling()) {
            if (tokens.contains(currentElement.getNode().getElementType())) return true;
        }
        return false;
    }

    /**
     * Checks whether the current, previous or next comments matches the regex
     *
     * @param element          the element from which to start the search
     * @param regex            the regex that should be matched
     * @param doNotCountMyself if true, at least 2 occurrences need to be found instead of 1
     * @return true if the regex could be matched across the comments
     */
    public static boolean doLinkedCommentsContain(@NotNull PsiElement element, String regex, boolean doNotCountMyself) {
        PsiElement javaElement;
        if (element.getLanguage().equals(JavaLanguage.INSTANCE)) {
            javaElement = element instanceof PsiComment ? getOwnerIfModifierList(element.getParent()) : element;
        } else {
            javaElement = getJavaContext(element);
        }
        ArrayList<PsiComment> comments = new ArrayList<>(PsiTreeUtil.getChildrenOfTypeAsList(javaElement, PsiComment.class));
        // comments in between Java modifiers, such as in "public /*@ pure @*/ static int f()"
        if (javaElement instanceof PsiModifierListOwner) {
            comments.addAll(PsiTreeUtil.getChildrenOfTypeAsList(((PsiModifierListOwner) javaElement).getModifierList(), PsiComment.class));
        }
        if (comments.isEmpty()) return false;
        return countTextMatchesAcrossComments(comments, regex, (doNotCountMyself ? 2 : 1)) >= (doNotCountMyself ? 2 : 1);
    }

    /**
     * @param comments comments, of which only the JML comments are searched
     * @param regex    the regex that should be matched, such as the name of a modifier
     * @return whether the regex matches in one of the comments, as a whole word after an at-sign, a semicolon or a
     * modifier, such as a modifier
     */
    public static boolean commentsContain(@NotNull List<PsiComment> comments, String regex) {
        return countTextMatchesAcrossComments(new ArrayList<>(comments), regex, 1) >= 1;
    }

    /**
     * Counts the amount of matches with the regex across the comments in the list
     *
     * @param comments the list of comments to search in
     * @param regex    the regex that should be matched
     * @param maxCount how many matches should be found to stop the search sooner
     * @return the number of matches found
     */
    private static int countTextMatchesAcrossComments(@NotNull ArrayList<PsiComment> comments, String regex, int maxCount) {
        // only captures whole words by using negative lookahead; the visibility of a specification case, as in
        // "public normal_behavior", is not a modifier
        Pattern pattern = PATTERNS.computeIfAbsent(regex,
                key -> Pattern.compile("(?<=@|;|\\*\\)|" + ALL_MODIFIERS_REGEX + ")\\s*(" + regex + ")(?!\\w)"
                        + "(?!\\s+(?:normal_|exceptional_)?behaviou?r(?!\\w))"));
        int count = 0;
        for (PsiComment comment : comments) {
            String commentText = jmlText(comment);
            // check that it is a JML comment; the modifiers in a ghost declaration or an invariant are not those of the
            // field or method below it
            if ((commentText.startsWith("/*@") || commentText.startsWith("//@")) && !CLASS_LEVEL.matcher(commentText).find()) {
                Matcher matcher = pattern.matcher(commentText);
                while (matcher.find()) {
                    count++;
                }
                if (count >= maxCount) return count;
            }
        }
        return count;
    }

    /**
     * Finds the first modifier that has a certain token type
     *
     * @param modifiers the list of modifiers
     * @param tokens    the tokens out of which one needs to match with the token-type of the modifier
     * @return a matching modifier, or null if it could not be found
     */
    public static PsiElement findFirstModifierInInvariant(JMLModifiers modifiers, TokenSet tokens) {
        if (modifiers == null) return null;
        for (PsiElement modifier = modifiers.getFirstChild(); modifier != null; modifier = modifier.getNextSibling()) {
            if (tokens.contains(modifier.getNode().getElementType())) return modifier;
        }
        return null;
    }

}
