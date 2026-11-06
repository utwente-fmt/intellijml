package nl.utwente.jmlplugin.parser;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static nl.utwente.jmlplugin.psi.JMLTypes.*;

%%

%{
  public _JMLLexer() {
    this((java.io.Reader)null);
  }
%}

%public
%class _JMLLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

EOL=\R
WHITE_SPACE=\s+

IDENT=[_$a-zA-Z][_$a-zA-Z0-9]*
WHITE_SPACE=[ \t\n\x0B\f\r]+
INFORMAL_DESCRIPTION=\([ \t\n\x0B\f\r]*\*([^*]|\*+[^*)])+\*[ \t\n\x0B\f\r]*\)
// a comment in JML, to the end of the line, such as "//@ assert x > 0; // why"; as in OpenJML, "//@" continues the
// JML, as in "//@ public model int side; //@ in size;", so there only "//" is a comment
LINE_COMMENT="//"([^@\r\n][^\r\n]*)?
// string and character literals, so that "//" or ";" in them does not end the Java expression
STRING_LITERAL=\"([^\"\\\r\n]|\\[^\r\n])*\"
CHAR_LITERAL='([^'\\\r\n]|\\[^\r\n])*'
REST=[^@;().,:*\[\]\s]

%%
<YYINITIAL> {
  {WHITE_SPACE}               { return WHITE_SPACE; }

  "signals_only"              { return SIGNALS_ONLY; }
  "\\not_specified"           { return NOT_SPECIFIED; }
  "\\nothing"                 { return NOTHING; }
  "\\everything"              { return EVERYTHING; }
  "\\into"                    { return INTO; }
  "spec_public"               { return SPEC_PUBLIC; }
  "spec_protected"            { return SPEC_PROTECTED; }
  "non_null"                  { return NON_NULL; }
  "nullable_by_default"       { return NULLABLE_BY_DEFAULT; }
  "non_null_by_default"       { return NON_NULL_BY_DEFAULT; }
  "loop_invariant"            { return LOOPINVARIANT; }
  "ghost"                     { return GHOST; }
  "model"                     { return MODEL; }
  "represents"                { return REPRESENTS; }
  "in"                        { return IN; }
  "reads"                     { return READS; }
  "old"                       { return OLD_KEYWORD; }
  "accessible"                { return ACCESSIBLE; }
  "set"                       { return SET; }
  "check"                     { return CHECK; }
  "show"                      { return SHOW; }
  "reachable"                 { return REACHABLE; }
  "unreachable"               { return UNREACHABLE; }
  "halt"                      { return HALT; }
  "split"                     { return SPLIT; }
  "assigns"                   { return ASSIGNS; }
  "diverges"                  { return DIVERGES; }
  "initially"                 { return INITIALLY; }
  "constraint"                { return CONSTRAINT; }
  "spec_pure"                 { return SPEC_PURE; }
  "strictly_pure"             { return STRICTLY_PURE; }
  "no_state"                  { return NO_STATE; }
  // the redundant forms of clauses, which repeat what the other clauses imply, as OpenJML accepts them; they are
  // checked as the clause they repeat
  "requires_redundantly"      { return REQUIRES; }
  "ensures_redundantly"       { return ENSURES; }
  "signals_redundantly"       { return SIGNALS; }
  "signals_only_redundantly"  { return SIGNALS_ONLY; }
  "assignable_redundantly"    { return ASSIGNABLE; }
  "modifiable_redundantly"    { return MODIFIABLE; }
  "modifies_redundantly"      { return MODIFIES; }
  "assigns_redundantly"       { return ASSIGNS; }
  "diverges_redundantly"      { return DIVERGES; }
  "accessible_redundantly"    { return ACCESSIBLE; }
  "reads_redundantly"         { return READS; }
  "invariant_redundantly"     { return INVARIANT; }
  "initially_redundantly"     { return INITIALLY; }
  "constraint_redundantly"    { return CONSTRAINT; }
  "assert_redundantly"        { return ASSERT; }
  "assume_redundantly"        { return ASSUME; }
  "loop_invariant_redundantly" { return LOOPINVARIANT; }
  "maintaining_redundantly"   { return MAINTAINING; }
  "decreases_redundantly"     { return DECREASES; }
  "decreasing_redundantly"    { return DECREASING; }
  "loop_decreases_redundantly" { return LOOP_DECREASES; }
  "loop_writes_redundantly"   { return LOOP_WRITES; }
  "loop_modifies_redundantly" { return LOOP_MODIFIES; }
  "loop_assigns_redundantly"  { return LOOP_ASSIGNS; }
  "code_java_math"            { return CODE_JAVA_MATH; }
  "spec_java_math"            { return SPEC_JAVA_MATH; }
  "code_safe_math"            { return CODE_SAFE_MATH; }
  "spec_safe_math"            { return SPEC_SAFE_MATH; }
  "code_bigint_math"          { return CODE_BIGINT_MATH; }
  "spec_bigint_math"          { return SPEC_BIGINT_MATH; }
  "decreases"                 { return DECREASES; }
  "decreasing"                { return DECREASING; }
  "loop_decreases"            { return LOOP_DECREASES; }
  "loop_writes"               { return LOOP_WRITES; }
  "loop_modifies"             { return LOOP_MODIFIES; }
  "loop_assigns"              { return LOOP_ASSIGNS; }
  "loop_assignable"           { return LOOP_ASSIGNABLE; }
  "normal_behavior"           { return NORMAL_BEHAVIOR; }
  "normal_behaviour"          { return NORMAL_BEHAVIOR; }
  "exceptional_behavior"      { return EXCEPTIONAL_BEHAVIOR; }
  "exceptional_behaviour"     { return EXCEPTIONAL_BEHAVIOR; }
  "behavior"                  { return BEHAVIOR; }
  "behaviour"                 { return BEHAVIOR; }
  "@"                         { return AT_SIGN; }
  ";"                         { return SEMICOLON; }
  "("                         { return L_PAREN; }
  ")"                         { return R_PAREN; }
  "."                         { return DOT; }
  ","                         { return COMMA; }
  ":"                         { return COLON; }
  "*"                         { return STAR; }
  "["                         { return L_SQ_BRAC; }
  "]"                         { return R_SQ_BRAC; }
  "also"                      { return ALSO; }
  "requires"                  { return REQUIRES; }
  "pre"                       { return PRE; }
  "ensures"                   { return ENSURES; }
  "post"                      { return POST; }
  "signals"                   { return SIGNALS; }
  "assignable"                { return ASSIGNABLE; }
  "modifiable"                { return MODIFIABLE; }
  "modifies"                  { return MODIFIES; }
  "invariant"                 { return INVARIANT; }
  "pure"                      { return PURE; }
  "instance"                  { return INSTANCE; }
  "helper"                    { return HELPER; }
  "nullable"                  { return NULLABLE; }
  "public"                    { return PUBLIC; }
  "private"                   { return PRIVATE; }
  "protected"                 { return PROTECTED; }
  "static"                    { return STATIC; }
  "final"                     { return FINAL; }
  "maintaining"               { return MAINTAINING; }
  "assert"                    { return ASSERT; }
  "assume"                    { return ASSUME; }
  "this"                      { return THIS; }
  "super"                     { return SUPER; }

  {IDENT}                     { return IDENT; }
  {WHITE_SPACE}               { return WHITE_SPACE; }
  {INFORMAL_DESCRIPTION}      { return INFORMAL_DESCRIPTION; }
  {LINE_COMMENT}              { return LINE_COMMENT; }
  {STRING_LITERAL}            { return REST; }
  {CHAR_LITERAL}              { return REST; }
  {REST}                      { return REST; }

}

[^] { return BAD_CHARACTER; }
