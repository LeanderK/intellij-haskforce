package com.haskforce.cabal.highlighting;

import com.intellij.lexer.FlexAdapter;

/**
 * The lexer for highlighting cabal files. The {@code _CabalSyntaxHighlightingLexer} gets auto-generated from the identically named lex file.
 */
public class CabalSyntaxHighlightingLexer extends FlexAdapter {
    public CabalSyntaxHighlightingLexer() {
        super(new _CabalSyntaxHighlightingLexer());
    }
}
