package com.haskforce.tools.cabal.projects

import com.haskforce.system.projects.BuildType.{Benchmark, Executable, Library, TestSuite}
import com.haskforce.system.projects.PackageManager.Cabal
import com.haskforce.system.projects.{GHCVersion, PackageManager, Project => BaseProject}
import com.haskforce.system.settings.HaskellBuildSettings
import com.haskforce.system.utils.ExecUtil.ExecError
import com.haskforce.system.utils.{ExecUtil, PQ}
import com.haskforce.tools.cabal.lang.psi
import com.haskforce.tools.cabal.lang.psi.{Benchmark, Executable, TestSuite}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
  * A Cabal Project
  */
class CabalProject(val psiFile: psi.CabalFile) extends BaseProject {
  private val LOG = Logger.getInstance(classOf[CabalProject])
  private val defaultSourceRoot : String = "."

  /**
    * Returns the name of the project
    */
  override def getName: Option[String] = for {
    pkgName <- PQ.getChildOfType(psiFile, classOf[psi.PkgName])
    ff <- PQ.getChildOfType(pkgName, classOf[psi.Freeform])
  } yield ff.getText

  /**
    * Returns the name of the project
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

  /**
    * Returns the active GHCVersion for the
    */
  override def getGHCVersion: Either[ExecUtil.ExecError, GHCVersion] = {
    val project: Project = psiFile.getProject
    val settings: HaskellBuildSettings = HaskellBuildSettings.getInstance(project)
    val path: Either[ExecUtil.ExecError, String] = settings.getGhcPath match {
      case null => Left(new ExecError("No GHC-path configured", null))
      case "" => Left(new ExecError("GHC path is empty", null))
      case x => Right(x)
    }

    path
      .right.flatMap(path => GHCVersion.getGHCVersion(null, path))
  }


  def canEqual(other: Any): Boolean = other.isInstanceOf[CabalProject]

  override def equals(other: Any): Boolean = other match {
    case that: CabalProject =>
      (that canEqual this) &&
        psiFile.getOriginalFile == that.psiFile.getOriginalFile
    case _ => false
  }

  override def hashCode(): Int = {
    val state = Seq(psiFile.getOriginalFile)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}
