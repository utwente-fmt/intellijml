package nl.utwente.jmlplugin.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import nl.utwente.jmlplugin.psi.impl.*;

/**
 * All element and token types of JML: the ones generated from JML.bnf (inherited from {@link JMLGrammarTypes}),
 * and the ones created by {@link nl.utwente.jmlplugin.parser.JMLJavaParser} and the lexers for JML inside
 * Java expressions, which the grammar does not know about.
 */
public interface JMLTypes extends JMLGrammarTypes {
    // the strings here are also displayed in syntax error messages to the user

    IElementType QUANTIFIED_EXPR = new JMLElementType("quantified expression");
    IElementType Q_VAR_DECL = new JMLElementType("variable declaration");
    IElementType TYPE_NAME = new JMLElementType("type");
    IElementType Q_VAR_DECL_IDENTIFIER = new JMLElementType("variable name");
    IElementType Q_PREDICATE = new JMLElementType("range condition");
    IElementType Q_SPEC_EXPR = new JMLElementType("expression");
    IElementType OLD_EXPR = new JMLElementType("\\old() expression");
    IElementType TYPE_OF_EXPR = new JMLElementType("\\typeof() expression");
    IElementType ELEM_TYPE_EXPR = new JMLElementType("\\elemtype() expression");
    IElementType TYPE_EXPR = new JMLElementType("\\type() expression");
    IElementType NON_NULL_ELEMENTS_EXPR = new JMLElementType("\\nonnullelements() expression");
    IElementType FRESH_EXPR = new JMLElementType("\\fresh() expression");
    IElementType INVARIANT_FOR_EXPR = new JMLElementType("\\invariant_for() expression");
    IElementType NOT_MODIFIED_EXPR = new JMLElementType("\\not_modified() expression");
    IElementType JAVA_MATH_EXPR = new JMLElementType("\\java_math() expression");
    IElementType SAFE_MATH_EXPR = new JMLElementType("\\safe_math() expression");
    IElementType BIGINT_MATH_EXPR = new JMLElementType("\\bigint_math() expression");
    IElementType INNER_JAVA_EXPR = new JMLElementType("expression");
    IElementType GHOST_DECLARATOR = new JMLElementType("ghost variable");

    IElementType JAVA_EXPR_TOKEN = new JMLTokenType("JAVA_EXPR_TOKEN");
    IElementType RESULT = new JMLTokenType("\\result");
    IElementType OLD = new JMLTokenType("\\old");
    IElementType FORALL = new JMLTokenType("\\forall");
    IElementType EXISTS = new JMLTokenType("\\exists");
    IElementType MAX = new JMLTokenType("\\max");
    IElementType MIN = new JMLTokenType("\\min");
    IElementType NUM_OF = new JMLTokenType("\\num_of");
    IElementType PRODUCT = new JMLTokenType("\\product");
    IElementType SUM = new JMLTokenType("\\sum");
    IElementType TYPE_OF = new JMLElementType("\\typeof");
    IElementType ELEM_TYPE = new JMLElementType("\\elemtype");
    IElementType TYPE = new JMLElementType("\\type");
    IElementType TYPE_CAPS = new JMLElementType("\\TYPE");
    IElementType NON_NULL_ELEMENTS = new JMLElementType("\\nonnullelements");
    IElementType FRESH = new JMLElementType("\\fresh");
    IElementType INVARIANT_FOR = new JMLElementType("\\invariant_for");
    IElementType NOT_MODIFIED = new JMLElementType("\\not_modified");
    // the arithmetic mode of an expression
    IElementType JAVA_MATH = new JMLElementType("\\java_math");
    IElementType SAFE_MATH = new JMLElementType("\\safe_math");
    IElementType BIGINT_MATH = new JMLElementType("\\bigint_math");
    // the number of iterations of a loop that are done
    IElementType COUNT = new JMLTokenType("\\count");
    // a labeled expression, such as (\lbl POSITIVE x > 0), whose value OpenJML reports
    IElementType LBL = new JMLTokenType("\\lbl");
    IElementType LBLPOS = new JMLTokenType("\\lblpos");
    IElementType LBLNEG = new JMLTokenType("\\lblneg");
    // a value of the variable for which the expression holds, such as (\choose int i; 0 < i; i < 2)
    IElementType CHOOSE = new JMLTokenType("\\choose");
    // the mathematical integers and reals
    IElementType BIGINT = new JMLTokenType("\\bigint");
    IElementType REAL = new JMLTokenType("\\real");
    // the type of a model field that only groups other fields, for assignable and reads clauses
    IElementType DATAGROUP = new JMLTokenType("\\datagroup");
    // a condition on the value of a model field, as in "represents size \such_that size >= 0;"
    IElementType SUCH_THAT = new JMLTokenType("\\such_that");
    IElementType LOGICAL_IMPL = new JMLElementType("==>");
    IElementType LOGICAL_EQ = new JMLElementType("<==>");
    IElementType LOGICAL_IMPL_REV = new JMLElementType("<==");
    IElementType LOGICAL_NOT_EQ = new JMLElementType("<=!=>");

    class Factory {
        public static PsiElement createElement(ASTNode node) {
            IElementType type = node.getElementType();
            if (type == QUANTIFIED_EXPR) {
                return new JMLQuantifiedExpr(node);
            } else if (type == Q_VAR_DECL) {
                return new JMLQVarDecl(node);
            } else if (type == TYPE_NAME) {
                return new JMLTypeName(node);
            } else if (type == Q_VAR_DECL_IDENTIFIER) {
                return new JMLQVarDeclIdentifier(node);
            } else if (type == Q_PREDICATE) {
                return new JMLQPredicate(node);
            } else if (type == Q_SPEC_EXPR) {
                return new JMLQSpecExpr(node);
            } else if (type == OLD_EXPR) {
                return new JMLOldExpr(node);
            } else if (type == TYPE_OF_EXPR) {
                return new JMLTypeOfExpr(node);
            } else if (type == ELEM_TYPE_EXPR) {
                return new JMLElemTypeExpr(node);
            } else if (type == TYPE_EXPR) {
                return new JMLTypeExpr(node);
            } else if (type == NON_NULL_ELEMENTS_EXPR) {
                return new JMLNonNullElementsExpr(node);
            } else if (type == FRESH_EXPR || type == INVARIANT_FOR_EXPR || type == NOT_MODIFIED_EXPR
                    || type == JAVA_MATH_EXPR || type == SAFE_MATH_EXPR || type == BIGINT_MATH_EXPR) {
                return new JMLBackslashFunctionExpr(node);
            } else if (type == GHOST_DECLARATOR) {
                return new JMLGhostDeclarator(node);
            } else if (type == INNER_JAVA_EXPR) {
                return new JMLInnerJavaExpr(node);
            }
            return JMLGrammarTypes.Factory.createElement(node);
        }
    }
}
