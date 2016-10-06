package com.haskforce.system.packages

import java.io.File
import java.util

import com.haskforce.haskell.{HaskellModuleBuilder, HaskellModuleType}
import com.haskforce.system.packages.BuildType.{Benchmark, Executable, Library, TestSuite}
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.{ModifiableModuleModel, Module, ModuleManager, ModuleServiceManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{ContentEntry, ModifiableRootModel, ModuleRootManager, ModuleRootModificationUtil}
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.{VfsUtilCore, VirtualFile, VirtualFileManager}
import com.intellij.util.Consumer

import scala.annotation.tailrec

/**
  * sets up an Intellij-Project based on the HPackages
  */
//TODO set module type
object ProjectSetup {
  private val LOG = Logger.getInstance(ProjectSetup.getClass)

  /**
    * Sets up an Intellij Project, updating existing modules. Must be run within a write action.
    * This method will for example change the package-manager of a package if it was managed by cabal before.
    * @param packages the packages
    * @param project the active project
    * @param setupRoot whether to create a
    * @return a List of shadowed Packages(packages that are contained, but are not equal to other packages),
    *         a List of updated Modules and a List of created Modules
    */
  def setUpWithUpdate(packages: List[HPackage], project: Project,
                      moduleModel: ModifiableModuleModel, projectRoot: String, setupRoot: Boolean)
                                                                  : (List[ExistingModule], List[Module], List[Module]) = {
    val (shadowed, existing, created) = setUpWithoutUpdate(packages, project, moduleModel, projectRoot, setupRoot)

    val updated: List[Module] = existing.map(existingModule => updateModule(existingModule.hPackage, existingModule.module, moduleModel, project))

    (shadowed, updated, created)
  }

  /**
    * Sets up an Intellij Project without updating existing modules. Must be run within a write action.
    * @param packages the packages
    * @param project the active project
    * @param setupRoot whether to create a
    * @return a List of shadowed Packages(packages that are contained, but are not equal to other packages),
    *         a List of existing Modules and a List of created Modules
    */
  def setUpWithoutUpdate(packages: List[HPackage], project: Project,
            moduleModel: ModifiableModuleModel, projectRoot: String, setupRoot: Boolean)
                                                        : (List[ExistingModule], List[ExistingModule], List[Module]) = {
    val (shadowed, existing, newPackages) = resolveModuleStatusForPackages(packages, project)

    var createdModules: List[Module] = newPackages.map(hPackage => createMissingModule(hPackage, moduleModel, project))
    // If we aren't updating an existing project AND the project root isn't a module,
    // let's create it as the "root" module.
    val existingModules: Set[File] = moduleModel.getModules
      .flatMap(module => ModuleRootManager.getInstance(module).getContentRoots)
      .filter(_.isInLocalFileSystem)
      .map(file => new File(file.getCanonicalPath))
      .toSet
    if (setupRoot && !existingModules.contains(new File(projectRoot))) {
      val projectName = new File(projectRoot).getName
      val rootPackage: Option[HPackage] = packages.find(pkg =>
        pkg.getLocation.isInLocalFileSystem
          && new File(pkg.getLocation.getParent.getCanonicalPath).equals(new File(projectRoot)))
      val projectModule = rootPackage match {
        case Some(pkg) => createMissingModule(pkg, moduleModel, project)
        case None => createDummyModule(project, moduleModel, projectRoot, projectName + " (root)")
      }
      createdModules = projectModule +: createdModules
    }
    (shadowed, existing, createdModules)
  }

  /**
    * adds a package, must be run within a write-action
    * @param hPackage the package to add
    * @param project the current project
    * @param update whether the existing should be updated
    * @return either an AddPackageError (won't return Existing if update is true) or the module
    */
  def addPackage(hPackage: HPackage, project: Project, update: Boolean): Either[AddPackageError, Module] = {
    val sourceDirs: Set[VirtualFile] = hPackage.getBuildInfo.toSet
      .flatMap(info => info.getSourceDirs)
      .flatMap(dir => com.haskforce.system.utils.FileUtil.fromRelativePath(dir, hPackage.getLocation.getParent.getPath))

    val sourceRootsModuleList: Array[(VirtualFile, Module)] = ModuleManager.getInstance(project).getModules
      .flatMap(module => ModuleRootManager.getInstance(module).getSourceRoots(true).zip(Stream.continually(module)))

    val moduleDirectories: Array[(VirtualFile, Module)] = ModuleManager.getInstance(project).getModules
      .flatMap(module => Option(module.getModuleFile).map(_.getParent).zip(Stream.continually(module)))

    val shadowed: Option[PackageShadowed] = getShadowed(sourceRootsModuleList, hPackage, sourceDirs)
      .map(existingModule => PackageShadowed(existingModule.matchingSourceDir, existingModule.module, existingModule.matchingContentRoot))
    if (shadowed.isDefined) {
      return Left(shadowed.get)
    }

    val modifiableModel: ModifiableModuleModel = ModuleManager.getInstance(project).getModifiableModel
    val matching: Option[Existing] = getExisting(moduleDirectories, hPackage)
      .map(existingModule => Existing(existingModule.module))
    if (matching.isDefined) {
      if (update) {
        Right(updateModule(hPackage, matching.get.module, modifiableModel, project))
      } else {
        Left(matching.get)
      }
    } else {
      Right(createMissingModule(hPackage, modifiableModel, project))
    }
  }

  /**
    * a package is shadowed if at least one of the source-directories is part of another module, but not equal
    * e.g. a subfolder in the src-content root of another module
    */
  def isShadowed(hPackage: HPackage, project: Project): Option[ExistingModule] = {
    val locations: Set[VirtualFile] = hPackage.getBuildInfo.toSet
      .flatMap(buildInfo => buildInfo.getSourceDirs)
      .flatMap(dir => com.haskforce.system.utils.FileUtil.fromRelativePath(dir, hPackage.getLocation.getParent.getPath))

    val sourceRootsModuleList: Array[(VirtualFile, Module)] = ModuleManager.getInstance(project).getModules
      .flatMap(module => ModuleRootManager.getInstance(module).getSourceRoots(true).zip(Stream.continually(module)))

    getShadowed(sourceRootsModuleList, hPackage, locations)
  }


  /**
    * determines whether a package is shadowed by an existing module, the module is already existing or is is new
    * @return (shadowed, existing, new)
    */
  private def resolveModuleStatusForPackages(packages: List[HPackage], project: Project): (List[ExistingModule], List[ExistingModule], List[HPackage]) = {
    val packageSourceDirList: List[(HPackage, Set[VirtualFile])] = packages.map(hPackage => {
      val sourceDirs: Set[VirtualFile] = hPackage.getBuildInfo.toSet
        .flatMap(info => info.getSourceDirs)
        .flatMap(dir => com.haskforce.system.utils.FileUtil.fromRelativePath(dir, hPackage.getLocation.getParent.getPath))
      (hPackage, sourceDirs)
    })

    val sourceRootsModuleList: Array[(VirtualFile, Module)] = ModuleManager.getInstance(project).getModules
      .flatMap(module => ModuleRootManager.getInstance(module).getSourceRoots(true).zip(Stream.continually(module)))


    //shadowed packageSourceDir, package, shadowing moduleContent, module
    val shadowed: List[ExistingModule] = packageSourceDirList.flatMap(tuple => {
      val (hPackage, locations) = tuple
      getShadowed(sourceRootsModuleList, hPackage, locations)
    })

    val shadowedPackages: Set[HPackage] = shadowed.map(existingModule => existingModule.hPackage).toSet

    val existing: List[ExistingModule] = packageSourceDirList
      .filter(tuple => !shadowedPackages.contains(tuple._1))
      .flatMap(tuple => {
        val (hPackage, locations) = tuple
        getExisting(sourceRootsModuleList, hPackage)
      })

    val existingPackages = existing.map(existingModule => existingModule.hPackage).toSet

    val newPackages: List[HPackage] = packages
      .filter(hPackage => !shadowedPackages.contains(hPackage) && !existingPackages.contains(hPackage))
    (shadowed, existing, newPackages)
  }

  private def getExisting(moduleDirectories: Array[(VirtualFile, Module)], hPackage: HPackage): Option[ExistingModule] = {
    moduleDirectories.toStream.filter(tuple => {
      val (contentRoot, module) = tuple
      contentRoot == hPackage.getLocation.getParent
    })
      .map(tuple => ExistingModule(hPackage.getLocation, hPackage, hPackage.getLocation, tuple._2))
      .headOption
  }

  private def getShadowed(contentRootsModuleList: Array[(VirtualFile, Module)], hPackage: HPackage, locations: Set[VirtualFile]): Option[ExistingModule] = {
    //a package is shadowed if at least one of the source-locations is inside another modules content root, but does not equal the content-root
    contentRootsModuleList.flatMap(tuple => {
      val (contentRoot, module) = tuple
      locations.find(location => VfsUtilCore.isAncestor(contentRoot, location, true))
        .map(packageContentRoot => ExistingModule(packageContentRoot, hPackage, contentRoot, module))
    }).headOption
  }

  /**
    * creates a Module for the package and registers the package, must be run within an write-action
    */
  private def createMissingModule(hPackage: HPackage, moduleModel: ModifiableModuleModel, project: Project) : Module = {
    val name: String = hPackage.getName.getOrElse(hPackage.getLocation.getNameWithoutExtension)
    val moduleDir: String = hPackage.getLocation.getParent.getPath
    val moduleName = determineProperModuleName(project, name, Set())
    val moduleBuilder = HaskellModuleType.getInstance.createModuleBuilder()
    moduleBuilder.setModuleFilePath(FileUtil.join(moduleDir, moduleName + ".iml"))
    moduleBuilder.setContentEntryPath(moduleDir)
    moduleBuilder.setName(moduleName)
    markDirectories(hPackage, moduleBuilder)
    val module = moduleBuilder.createModule(moduleModel)
    HPackageModule.getInstance(module).replacePackage(hPackage)
    moduleBuilder.commit(project)
    module
  }

  /**
    * updates the Module,
    */
  private def updateModule(newPackage: HPackage, module: Module, moduleModel: ModifiableModuleModel, project: Project): Module = {
    val packageModule: HPackageModule = ModuleServiceManager.getService(module, classOf[HPackageModule])
    val oldPackage: Option[HPackage] = packageModule.optPackage
    ModuleRootModificationUtil.updateModel(module, new Consumer[ModifiableRootModel] {
      override def consume(modifiableRootModel: ModifiableRootModel): Unit = {
        //modifiableRootModel.clear()
        updateModifiableRootModel(modifiableRootModel, newPackage)
      }
    })
    if (module.getName != newPackage.getName.getOrElse(newPackage.getLocation.getNameWithoutExtension)) {
      val newModuleName = determineProperModuleName(project, newPackage.getName.getOrElse(newPackage.getLocation.getNameWithoutExtension), Set(module.getName))
      moduleModel.renameModule(module, newModuleName)
      moduleModel.commit()
    }
    packageModule.replacePackage(newPackage)
    oldPackage.foreach(pkg => pkg.emitEvent(Replace(newPackage)))
    module
  }

  private def createDummyModule(project: Project, moduleModel: ModifiableModuleModel, moduleDir: String, moduleName: String): Module = {
    val moduleFilePath = FileUtil.join(moduleDir, s"$moduleName.iml")
    val moduleBuilder = HaskellModuleType.getInstance.createModuleBuilder()
    moduleBuilder.setModuleFilePath(moduleFilePath)
    moduleBuilder.setContentEntryPath(moduleDir)
    moduleBuilder.setName(moduleName)
    moduleBuilder.setSourcePaths(util.Collections.emptyList())
    val module = moduleBuilder.createModule(moduleModel)
    moduleBuilder.commit(project)
    module
  }

  /**
    * sets the source-directories etc. for the package
    */
  private def markDirectories(hPackage: HPackage, moduleBuilder: HaskellModuleBuilder): Unit = {
    val moduleDir: String = hPackage.getLocation.getParent.getPath
    val sourcePaths: Set[String] = hPackage.getBuildInfo
      .toSet
      .filter(info => info.typ match {
        case Library => true
        case Executable => true
        case other => false
      })
      .flatMap(info => info.getSourceDirs)

    //to avoid the Java default 'src/'
    moduleBuilder.setSourcePaths(util.Collections.emptyList())
    if (sourcePaths.nonEmpty) {
      sourcePaths.foreach { dir =>
        moduleBuilder.addSourcePath(Pair.create(FileUtil.join(moduleDir, dir), ""))
      }
    }
    // '.addSourcePath' doesn't support test sources, so we must do this manually.
    moduleBuilder.addModuleConfigurationUpdater(new ModuleBuilder.ModuleConfigurationUpdater {
      override def update(module: Module, rootModel: ModifiableRootModel): Unit = {
        updateModifiableRootModel(rootModel, hPackage)
      }
    })
  }

  private def updateModifiableRootModel(modifiableRootModel: ModifiableRootModel, hPackage: HPackage): Unit = {
    val moduleDir: String = hPackage.getLocation.getParent.getPath
    val vFileMgr = VirtualFileManager.getInstance()
    val markDirectories = (ce: ContentEntry, directories: Set[String], doMark: String => Unit) => {
      if (directories.nonEmpty) {
        directories.foreach(dir => {
          val file: File = new File(s"${ce.getFile.getCanonicalPath}/$dir")
          if (!file.exists() && !file.mkdirs()) {
            LOG.warn(new AssertionError(
              s"Could not create directory: ${ce.getFile.getCanonicalPath}/$dir"
            ))
          }
          Option(vFileMgr.findFileByUrl(s"${ce.getUrl}/$dir")) match {
            case Some(vDir) if vDir.isDirectory =>
              doMark(FileUtil.join(ce.getUrl, dir))
            case Some(_) =>
              LOG.warn(new AssertionError(
                s"Path is not a directory: $dir (relative to ${ce.getFile.getPath})"
              ))
            case None =>
              LOG.warn(new AssertionError(
                s"VirtualFile not found: $dir (relative to ${ce.getFile.getPath})"
              ))
          }
        })
      }
    }
    modifiableRootModel.getContentEntries.collectFirst {
      case ce if ce.getFile.getPath == moduleDir => ce
    } match {
      case Some(ce) =>
        val sourceDirs: Set[String] = hPackage.getSources
          .flatMap(info => info.getSourceDirs)

        val testDirs: Set[String] = hPackage.getTests
          .flatMap(info => info.getSourceDirs)
          .diff(sourceDirs)
        markDirectories(ce, testDirs, path => ce.addSourceFolder(path, /* isTestSource */ true))
        markDirectories(ce, sourceDirs, path => ce.addSourceFolder(path, /* isTestSource */ false))
      case None =>
        LOG.warn(new AssertionError(
          s"Could not find content entry for module with path: $moduleDir"
        ))
    }
  }

  /**
    * Determines a proper module name that doesn't clash with existing modules.
    */
  private def determineProperModuleName(project: Project, name: String, exclude: Set[String]): String = {
    val moduleNames = ModuleManager.getInstance(project).getModules
      .map(_.getName)
      .filter(name => !exclude.contains(name))
      .map(_.toLowerCase)
    @tailrec
    def loop(suffix: Int): String = {
      // Append "-suffix" to name if there are conflicts.
      val newName = name + (if (suffix == 0) "" else "-" + suffix)
      if (moduleNames.contains(newName)) loop(suffix + 1)
      else newName
    }
    loop(0)
  }
}

case class ExistingModule(matchingSourceDir: VirtualFile, hPackage: HPackage, matchingContentRoot: VirtualFile, module: Module)

sealed trait AddPackageError
case class PackageShadowed(shadowedSourceDir: VirtualFile, module: Module, shadowingContentRoot: VirtualFile) extends AddPackageError
case class Existing(module: Module) extends AddPackageError
