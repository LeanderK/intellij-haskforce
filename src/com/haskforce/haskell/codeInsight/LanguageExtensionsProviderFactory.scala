package com.haskforce.haskell.codeInsight

import com.haskforce.haskell.constants.GhcLanguageExtensions
import com.intellij.psi.PsiFile

object LanguageExtensionsProviderFactory {
  def get(psiFile: PsiFile): Option[LanguageExtensionsProvider] = {
    Some(GhcLanguageExtensionsProvider)
  }
}

trait LanguageExtensionsProvider {
  def getLanguages: Array[String]
}

object GhcLanguageExtensionsProvider extends LanguageExtensionsProvider {
  override def getLanguages: Array[String] = GhcLanguageExtensions.stringArray
}
