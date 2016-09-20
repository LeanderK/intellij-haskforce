package com.haskforce.tools.cabal.packages

import com.haskforce.system.packages.BuildType.{Benchmark, Executable, Library, TestSuite}
import com.haskforce.system.packages.PackageManager.Cabal
import com.haskforce.system.packages.{PackageManager, HPackage => BaseHPackage}
import com.haskforce.system.utils.PQ
import com.haskforce.tools.cabal.lang.psi
import com.haskforce.tools.cabal.lang.psi.{Benchmark, Executable, TestSuite}
import com.intellij.openapi.vfs.VirtualFile

/**
  * A Cabal Package
  */
class CabalPackage(val psiFile: psi.CabalFile) extends BaseHPackage {
  val defaultSourceRoot : String = "."

  /**
    * Returns the name of the package
    */
  override def getName: Option[String] = for {
    pkgName <- PQ.getChildOfType(psiFile, classOf[psi.PkgName])
    ff <- PQ.getChildOfType(pkgName, classOf[psi.Freeform])
  } yield ff.getText

  /**
    * Returns the name of the package
    */
  override def getLocation: VirtualFile = psiFile.getOriginalFile.getVirtualFile

  /**
    * If 'library' stanza exists, returns it; otherwise, implicitly uses root stanza.
    */
  def getLibrary: cabalBuildInfo = {
    psiFile.getChildren.collectFirst {
      case c: psi.Library => new cabalBuildInfo(c, Library, defaultSourceRoot)
    }.getOrElse(new cabalBuildInfo(psiFile, Library, defaultSourceRoot))
  }

  /**
    * Returns the associated BuildInfos
    */
  override def getBuildInfo: List[cabalBuildInfo] = {
    getLibrary +: psiFile.getChildren.collect {
      case c: Executable => new cabalBuildInfo(c, Executable, defaultSourceRoot)
      case c: TestSuite => new cabalBuildInfo(c, TestSuite, defaultSourceRoot)
      case c: Benchmark => new cabalBuildInfo(c, Benchmark, defaultSourceRoot)
    }.toList
  }

  /**
    * Returns the corresponding PackageManager
    */
  override def getPackageManager: PackageManager = Cabal


  def canEqual(other: Any): Boolean = other.isInstanceOf[CabalPackage]

  override def equals(other: Any): Boolean = other match {
    case that: CabalPackage =>
      (that canEqual this) &&
        psiFile.getOriginalFile == that.psiFile.getOriginalFile
    case _ => false
  }

  override def hashCode(): Int = {
    val state = Seq(psiFile.getOriginalFile)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}
