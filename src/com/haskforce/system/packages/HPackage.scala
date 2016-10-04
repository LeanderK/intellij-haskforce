package com.haskforce.system.packages

import com.haskforce.system.utils.{ExecUtil, NonEmptySet}
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import rx.lang.scala.Subject

/**
  * Holds information about a haskell package (essentially a cabal-project)
  */
trait HPackage {
  private val eventSource : Subject[PackageEvent] = Subject()
  /**
    * Returns the name of the package
    */
  def getName: Option[String]

  /**
    * Returns the location of the package
    */
  def getLocation: VirtualFile

  /**
    * Returns the associated BuildInfos
    */
  def getBuildInfo: NonEmptySet[BuildInfo]

  /**
    * Returns the corresponding PackageManager
    */
  def getPackageManager: BackingPackageManager

  /**
    * Returns the active GHCVersion for the package or an error
    */
  def getGHCVersion: Either[ExecUtil.ExecError, GHCVersion]

  /**
    * use this Subject to subscribe to events.
    * ==example usage==
    * {{{
    * val subscription = package.getEvents.subscribe(event => println(event))
    * subscription.unsubscribe()
    * }}}
    */
  def getEvents: Subject[PackageEvent] = eventSource

  /**
    * returns the best Matching BuildInfo (if none matches, simply head of getBuildInfo)
    * @param sourcePath the sourcePath
    * @return
    */
  def getBestMatchingBuildInfo(sourcePath: String): BuildInfo = {
    val parent: VirtualFile = getLocation.getParent
    if (parent == null) return getBuildInfo.toSet.head
    val baseDir: String = parent.getCanonicalPath
    if (baseDir == null) return getBuildInfo.toSet.head
    getBuildInfo.toSet.toStream
      .find(info => {
        info.getSourceDirs.toStream
          .exists(sourceDir => FileUtil.isAncestor(FileUtil.join(baseDir, sourceDir), sourcePath, true))
      })
      .getOrElse(getBuildInfo.toSet.head)
  }

  /**
    * returns information about the project (if existing)
    */
  def getProjectInformation: Option[ProjectInformation]

  /**
    * emits a new PackageEvent to all the Observers
    * @param packageEvent the Event to emit
    */
  private[packages] def emitEvent(packageEvent: PackageEvent) = eventSource.onNext(packageEvent)

  /**
    * returns the state, used to persists the package
    */
  def getState: HPackageState
}

@SerialVersionUID(1L)
trait HPackageState extends Serializable {
  /**
    * the name of the PackageManager
    */
  def getPackageManager: String
}

/**
  * the different Events for the HPackage
  */
sealed trait PackageEvent

/**
  * if the package got completely removed
  */
case class Remove() extends PackageEvent

/**
  * if the package got replaced by a new package from a different Package-Manager
  */
case class Replace(newPackage: HPackage) extends PackageEvent

/**
  * if the package got replaced by a package from the same Package-Manager
  */
case class Update(newPackage: HPackage) extends PackageEvent
