package com.haskforce.parsing;
import com.intellij.lexer.*;
import com.intellij.psi.tree.IElementType;
import static com.haskforce.psi.HaskellTypes.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.Stack;

/**
 * Hand-written lexer used for parsing in IntelliJ.
 *
 * We share token names with the grammar-kit generated
 * parser.
 *
 * Massively changed, but originally derived from the lexer generated by
 * Grammar-Kit at 29 April 2014.
 */


/*
 * To generate sources from this file -
 *   Click Tools->Run JFlex generator.
 *
 * Command-Shift-G should be the keyboard shortcut, but that is the same
 * shortcut as find previous.
 */


%%

%{
  private int commentLevel;
  private int indent;
  private boolean retry;
  private boolean emptyblock;
  private Stack<Pair<Integer,Integer>> indentationStack;
  // %line/%%column does not declare these.
  private int yyline;
  private int yycolumn;

  public _HaskellParsingLexer() {
    this((java.io.Reader)null);
    retry = false;
    emptyblock = false;
    indentationStack = ContainerUtil.newStack();
  }
%}

/*
 * Missing lexemes: by, haddock things.
 *
 * Comments: one line too many in dashes-comments.
 */

%public
%class _HaskellParsingLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode
%line
%column

EOL=\r|\n|\r\n
VARIDREGEXP=([a-z_][a-zA-Z_0-9']+)|[a-z]
CONID=[A-Z][a-zA-Z_0-9']*
CHARTOKEN='(\\.|[^'])'
INTEGERTOKEN=(0(o|O)[0-7]+|0(x|X)[0-9a-fA-F]+|[0-9]+)
FLOATTOKEN=([0-9]+\.[0-9]+((e|E)(\+|\-)?[0-9]+)?|[0-9]+((e|E)(\+|\-)?[0-9]+))
COMMENT=--([^\^\r\n][^\r\n]*\n|[\r\n])
HADDOCK=--\^[^\r\n]*
CPPIF=#if ([^\r\n]*)
ASCSYMBOL=[\!\#\$\%\&\*\+\.\/\<\=\>\?\@\\\^\|\-\~\:]

STRINGGAP=\\[ \t\n\x0B\f\r]*\n[ \t\n\x0B\f\r]*\\

// Avoid "COMMENT" since that collides with the token definition above.
%state REALLYYINITIAL INCOMMENT INSTRING INPRAGMA ININDENTATION FINDINGINDENTATIONCONTEXT

%%

<<EOF>>             {
                        if (indentationStack.size() > 0) {
                            indentationStack.pop();
                            return WHITESPACERBRACETOK;
                        }
                        return null;
                    }

<YYINITIAL,REALLYYINITIAL,ININDENTATION,FINDINGINDENTATIONCONTEXT> {
    {COMMENT}       { indent = 0; return COMMENT; }
    {HADDOCK}       { indent = 0; return HADDOCK; }
    {CPPIF}         { indent = 0; return CPPIF; }
    "#else"         { indent = 0; return CPPELSE; }
    "#endif"        { indent = 0; return CPPENDIF; }
}

<YYINITIAL> {
    {EOL}+          {
                        indent = 0;
                        return com.intellij.psi.TokenType.WHITE_SPACE;
                    }
    [\ \f]          {
                        indent++;
                        return com.intellij.psi.TokenType.WHITE_SPACE;
                    }
    [\t]            {
                        indent = indent + (indent + 8) % 8;
                        return com.intellij.psi.TokenType.WHITE_SPACE;
                    }
    ("{"[^\-])      {
                        yybegin(REALLYYINITIAL);
                        yypushback(2);
                    }
    "module"        {
                        yybegin(REALLYYINITIAL);
                        yypushback(6);
                    }
    [^]             {
                        indentationStack.push(Pair.create(yyline, yycolumn));
                        yybegin(REALLYYINITIAL);
                        yypushback(1);
                        return WHITESPACELBRACETOK;
                    }

}

<REALLYYINITIAL> {
  {EOL}               {
                        yybegin(ININDENTATION);
                        indent = 0;
                        return com.intellij.psi.TokenType.WHITE_SPACE;
                      }
    [\ \f]          {
                        indent++;
                        return com.intellij.psi.TokenType.WHITE_SPACE;
                    }
    [\t]            {
                        indent = indent + (indent + 8) % 8;
                        return com.intellij.psi.TokenType.WHITE_SPACE;
                    }

  "class"             { return CLASSTOKEN; }
  "data"              { return DATA; }
  "default"           { return DEFAULT; }
  "deriving"          { return DERIVING; }
  "export"            { return EXPORTTOKEN; }
  "foreign"           { return FOREIGN; }
  "instance"          { return INSTANCE; }
  "module"            { return MODULETOKEN; }
  "newtype"           { return NEWTYPE; }
  "type"              { return TYPE; }
  "where"               {
                            yybegin(FINDINGINDENTATIONCONTEXT);
                            indent = yycolumn;
                            return WHERE;
                        }
  "as"                { return AS; }
  "import"            { return IMPORT; }
  "infix"             { return INFIX; }
  "infixl"            { return INFIXL; }
  "infixr"            { return INFIXR; }
  "qualified"         { return QUALIFIED; }
  "hiding"            { return HIDING; }
  "\\case"            {
                          yybegin(FINDINGINDENTATIONCONTEXT);
                          indent = yycolumn;
                          return LCASETOK;
                      }
  "case"              { return CASE; }
  "do"                  {
                            yybegin(FINDINGINDENTATIONCONTEXT);
                            indent = yycolumn;
                            return DO;
                        }
  "else"              { return ELSE; }
  "if"                { return IF; }
  "in"                {
                            if (retry) {
                                retry = false;
                            } else if (!indentationStack.isEmpty() &&
                                        yyline ==
                                           indentationStack.peek().getFirst()) {
                                indentationStack.pop();
                                yypushback(2);
                                retry = true;
                                return WHITESPACERBRACETOK;
                            }
                            return IN;
                        }
  "let"                 {
                            yybegin(FINDINGINDENTATIONCONTEXT);
                            indent = yycolumn;
                            return LET;
                        }
  "of"                  {
                            yybegin(FINDINGINDENTATIONCONTEXT);
                            indent = yycolumn;
                            return OF;
                        }
  "then"              { return THEN; }
  "forall"            { return FORALLTOKEN; }

  "<-"                { return LEFTARROW; }
  "->"                { return RIGHTARROW; }
  "=>"                { return DOUBLEARROW; }
  "\\&"               { return NULLCHARACTER; }
  "(#"                { return LUNBOXPAREN; }
  "#)"                { return RUNBOXPAREN; }
  "("                 { return LPAREN; }
  ")"                 { return RPAREN; }
  "|"                 { return PIPE; }
  ","                 { return COMMA; }
  ";"                 { return SEMICOLON; }
  "["                 { return LBRACKET; }
  "]"                 { return RBRACKET; }
  "''"                { return THQUOTE; }
  "`"                 { return BACKTICK; }
  "\""                {
                        yybegin(INSTRING);
                        return DOUBLEQUOTE;
                      }
  "{-#"               {
                        yybegin(INPRAGMA);
                        return OPENPRAGMA;
                      }
  "{-"                {
                        commentLevel = 1;
                        yybegin(INCOMMENT);
                        return OPENCOM;
                      }
  "{"                 { return LBRACE; }
  "}"                 { return RBRACE; }
  "'"                 { return SINGLEQUOTE; }
  "!"                 { return EXCLAMATION; }
  "##"                { return DOUBLEHASH; }
  "#"                 { return HASH; }
  "$"                 { return DOLLAR; }
  "%"                 { return PERCENT; }
  "&"                 { return AMPERSAND; }
  "*"                 { return ASTERISK; }
  "+"                 { return PLUS; }
  ".."                { return DOUBLEPERIOD; }
  "."                 { return PERIOD; }
  "/"                 { return SLASH; }
  "<"                 { return LESSTHAN; }
  "="                 { return EQUALS; }
  ">"                 { return GREATERTHAN; }
  "?"                 { return QUESTION; }
  "@"                 { return AMPERSAT; }
  "\\"                { return BACKSLASH; }
  "^"                 { return CARET; }
  "-"                 { return MINUS; }
  "~"                 { return TILDE; }
  "_"                 { return UNDERSCORE; }
  "::"                { return DOUBLECOLON; }
  ":"                 { return COLON; }
  (":"{ASCSYMBOL}+)     { return CONSYMTOK; }
  ({ASCSYMBOL}+)      { return VARSYMTOKPLUS; }

  {VARIDREGEXP}       { return VARIDREGEXP; }
  {CONID}             { return CONIDREGEXP; }
  {CHARTOKEN}         { return CHARTOKEN; }
  {INTEGERTOKEN}      { return INTEGERTOKEN; }
  {FLOATTOKEN}        { return FLOATTOKEN; }
  [^] { return com.intellij.psi.TokenType.BAD_CHARACTER; }
}

<INCOMMENT> {
    "-}"              {
                        commentLevel--;
                        if (commentLevel == 0) {
                            yybegin(REALLYYINITIAL);
                            return CLOSECOM;
                        }
                        return COMMENTTEXT;
                      }

    "{-"              {
                        commentLevel++;
                        return COMMENTTEXT;
                      }

    [^-{}]+           { return COMMENTTEXT; }
    [^]               { return COMMENTTEXT; }
}

<INSTRING> {
    \"                              {
                                        yybegin(REALLYYINITIAL);
                                        return DOUBLEQUOTE;
                                    }
    (\\)+                           { return STRINGTOKEN; }
    ({STRINGGAP}|\\\"|[^\"\\\n])+   { return STRINGTOKEN; }

    [^]                             { return BADSTRINGTOKEN; }
}

<INPRAGMA> {
    "#-}"           {
                        yybegin(REALLYYINITIAL);
                        return CLOSEPRAGMA;
                    }
    [^-}#]+         { return PRAGMA; }
    [^]             { return PRAGMA; }
}

<ININDENTATION> {
    [\ \f]          {
                        indent++;
                        return com.intellij.psi.TokenType.WHITE_SPACE;
                    }
    [\t]            {
                        indent = indent + (indent + 8) % 8;
                        return com.intellij.psi.TokenType.WHITE_SPACE;
                    }
    [\n]            {
                        indent = 0;
                        return com.intellij.psi.TokenType.WHITE_SPACE;
                    }
   "--"[^\r\n]*     {   // DO NOT REMOVE.
                        // Workaround for {COMMENT} not affecting this rule.
                        // See Comment00004.hs for test case.
                        indent = 0;
                        return COMMENT;
                    }
    [^]             {
                        if (!indentationStack.isEmpty() && indent == indentationStack.peek().getSecond()) {
                            yybegin(REALLYYINITIAL);
                            yypushback(1);
                            return WHITESPACESEMITOK;
                        } else if (!indentationStack.isEmpty() && indent < indentationStack.peek().getSecond()) {
                            indentationStack.pop();
                            yypushback(1);
                            return WHITESPACERBRACETOK;
                        }
                        yybegin(REALLYYINITIAL);
                        yypushback(1);
                    }
}

<FINDINGINDENTATIONCONTEXT> {
    [\ \f]          {
                        indent++;
                        return com.intellij.psi.TokenType.WHITE_SPACE;
                    }
    [\t]            {
                        indent = indent + (indent + 8) % 8;
                        return com.intellij.psi.TokenType.WHITE_SPACE;
                    }
    [\n]            {
                        indent = 0;
                        return com.intellij.psi.TokenType.WHITE_SPACE;
                    }
    ("{"[^\-])     {

                        yybegin(REALLYYINITIAL);
                        yypushback(1);
                        return LBRACE;
                    }
    <<EOF>>         {   // Deal with "module Modid where \n\n\n".
                        indentationStack.push(Pair.create(yyline, yycolumn));
                        yybegin(REALLYYINITIAL);
                        return WHITESPACELBRACETOK;
                    }
    [^]             {
                        yypushback(1);
                        if (emptyblock) {
                            emptyblock = false;
                            yybegin(REALLYYINITIAL);
                            return WHITESPACERBRACETOK;
                        } else if (indentationStack.size() > 1 &&
                                yycolumn == indentationStack.peek().getSecond()) {
                            emptyblock = true;
                            return WHITESPACELBRACETOK;
                        } else {
                            indentationStack.push(Pair.create(yyline, yycolumn));
                            yybegin(REALLYYINITIAL);
                            return WHITESPACELBRACETOK;
                        }
                    }
}
