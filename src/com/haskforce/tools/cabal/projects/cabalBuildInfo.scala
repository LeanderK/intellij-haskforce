package com.haskforce.tools.cabal.projects

import com.haskforce.system.projects.{BuildType, BuildInfo => BaseBuildInfo}
import com.haskforce.system.utils.{NonEmptySet, PQ}
import com.haskforce.tools.cabal.lang.psi
import com.haskforce.tools.cabal.lang.psi.CabalTypes
import com.intellij.psi.PsiElement

/**
  * A Cabal BuildInfo
  */
class cabalBuildInfo(val el: PsiElement, val bType : BuildType, val defaultSourceRoot : String) extends BaseBuildInfo {
  override val typ: BuildType = bType

  /**
    * Returns all listed haskell-extensions.
    */
  override def getExtensions: Set[String] = {
    PQ.streamChildren(el, classOf[psi.impl.ExtensionsImpl]).flatMap(
      PQ.getChildOfType(_, classOf[psi.IdentList])
    ).flatMap(
      PQ.getChildNodes(_, CabalTypes.IDENT).map(_.getText)
    ).toSet
  }

  /**
    * Returns the aggregated dependencies' package names.
    */
  override def getDependencies: Set[String] = {
    PQ.getChildOfType(el, classOf[psi.BuildDepends]).map(
      _.getPackageNames.toSet
    ).getOrElse(Set.empty)
  }

  /**
    * Returns the aggregated GHC-Options
    */
  override def getGhcOptions: Set[String] = {
    PQ.streamChildren(el, classOf[psi.impl.GhcOptionsImpl]).flatMap(_.getValue).toSet
  }

  /**
    * Get hs-source-dirs listed, defaulting to "." if not present.
    */
  override def getSourceDirs: NonEmptySet[String] = {
    NonEmptySet.fromSets[String](
      PQ.streamChildren(el, classOf[psi.impl.SourceDirsImpl]).map(_.getValue.toSet)
    ).getOrElse(NonEmptySet(defaultSourceRoot))
  }
}
