package com.haskforce

import java.awt.event.ActionEvent
import java.awt.{Color, GridBagLayout}
import java.io.{File, IOException, PrintWriter}
import java.util.concurrent.ExecutionException
import javax.swing._

import scala.collection.mutable

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.util.projectWizard._
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.module.{Module, ModuleType}
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.{Project, ProjectManager}
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.ErrorLabel
import com.intellij.uiDesigner.core.Spacer
import org.apache.commons.lang.builder.HashCodeBuilder
import org.jetbrains.annotations.{NotNull, Nullable}

import com.haskforce.Implicits._
import com.haskforce.cabal.settings.CabalComponentType
import com.haskforce.cabal.settings.ui.NewCabalProjectForm
import com.haskforce.macros.string.dedent
import com.haskforce.settings.{HaskellBuildSettings, ToolKey}
import com.haskforce.ui.GC
import com.haskforce.utils.{GuiUtil, Logging}

/**
 * Manages the creation of Haskell modules via interaction with the user.
 */
class HaskellModuleBuilder extends JavaModuleBuilder with SourcePathsBuilder with ModuleBuilderListener {
  val LOG = Logger.getInstance(getClass)

  @throws(classOf[ConfigurationException])
  override def setupRootModel(rootModel: ModifiableRootModel) {
    addListener(this)
    setupRootModelCallbacks.foreach { _(rootModel) }
    super.setupRootModel(rootModel)
  }

  /**
   * Method provided so the HaskellCompilerToolsForm can tell us how to update the project settings.
   */
  def registerSetupRootModelCallback(callback: ModifiableRootModel => Unit): Unit = {
    setupRootModelCallbacks += callback
  }
  val setupRootModelCallbacks = new mutable.MutableList[ModifiableRootModel => Unit]()

  /**
   * Returns the Haskell module type.
   */
  override def getModuleType: ModuleType[_ <: ModuleBuilder] = HaskellModuleType.getInstance

  /**
   * Ensures that SDK type is a Haskell SDK.
   */
  override def isSuitableSdkType(sdkType: SdkTypeId): Boolean = sdkType == HaskellSdkType.getInstance

  /**
   * Called after module is created.
   */
  def moduleCreated(@NotNull module: Module) {
    val haskellIgnoredList = Set("*.dyn_hi", "*.dyn_hi", "*.dyn_o", "*.hi", "*.o")
    val fileTypeManager = FileTypeManager.getInstance
    val newIgnoredList = fileTypeManager.getIgnoredFilesList.split(';').toSet.union(haskellIgnoredList)
    fileTypeManager.setIgnoredFilesList(newIgnoredList.mkString(";"))
  }

  override def createWizardSteps
      (wizardContext: WizardContext,
       modulesProvider: ModulesProvider)
      : Array[ModuleWizardStep] = {
    if (wizardContext.isCreatingNewProject) {
      Array(
        HaskellBuildToolStep(this, wizardContext),
        HaskellToolsSettingsStep(this, wizardContext),
        HaskellCabalPackageSettingsStep(this, wizardContext)
      )
    } else {
      Array()
    }
  }

  @Nullable
  override def modifySettingsStep(@NotNull settingsStep: SettingsStep): ModuleWizardStep = {
    new HaskellModifiedSettingsStep(this, settingsStep)
  }

  override def hashCode: Int = HashCodeBuilder.reflectionHashCode(this)
}

case class HaskellToolsSettingsStep(
  moduleBuilder: HaskellModuleBuilder,
  wizardContext: WizardContext
) extends ModuleWizardStep {
  val form = new HaskellToolsSettingsStepForm(wizardContext)

  override def getComponent: JComponent = form.contentPane

  override def updateDataModel(): Unit = {
    moduleBuilder.registerSetupRootModelCallback { rootModel: ModifiableRootModel =>
      val project = rootModel.getProject
      val defaultProject = ProjectManager.getInstance.getDefaultProject
      val toolSettings = PropertiesComponent.getInstance(project)
      val defaultToolSettings = PropertiesComponent.getInstance(defaultProject)
      List(
        (ToolKey.GHC_MOD_KEY, form.ghcModPathField, form.ghcModSetDefault),
        (ToolKey.HLINT_KEY, form.hlintPathField, form.hlintSetDefault),
        (ToolKey.STYLISH_HASKELL_KEY, form.stylishPathField, form.stylishSetDefault)
      ).foreach { case (key, field, setDefault) =>
        if (field.getText.nonEmpty) {
          toolSettings.setValue(key.pathKey, field.getText)
          if (setDefault.isSelected) {
            defaultToolSettings.setValue(key.pathKey, field.getText)
          }
        }
      }
    }
  }

  override def validate(): Boolean = {
    var result = true
    List(
      ("ghc-mod", form.ghcModPathField, form.ghcModPathErrorsField),
      ("hlint", form.hlintPathField, form.hlintPathErrorsField),
      ("stylish-haskell", form.stylishPathField, form.stylishPathErrorsField)
    ).foreach { case (name, field, errors) =>
      if (field.getText.nonEmpty && !new File(field.getText).canExecute) {
        errors.setErrorText(s"Invalid $name path", Color.red)
        result = false
      } else {
        errors.setText("")
      }
    }
    result
  }
}

class HaskellToolsSettingsStepForm(wizardContext: WizardContext) {
  val ghcModPathField = new TextFieldWithBrowseButton
  val ghcModPathErrorsField = new ErrorLabel
  val ghcModAutoFindButton = new JButton("Auto Find")
  val ghcModSetDefault = new JCheckBox("Set as default")
  GuiUtil.addFolderListener(ghcModPathField, "ghc-mod")
  GuiUtil.addApplyPathAction(ghcModAutoFindButton, ghcModPathField, "ghc-mod")

  val hlintPathField = new TextFieldWithBrowseButton
  val hlintPathErrorsField = new ErrorLabel
  val hlintAutoFindButton = new JButton("Auto Find")
  val hlintSetDefault = new JCheckBox("Set as default")
  GuiUtil.addFolderListener(hlintPathField, "hlint")
  GuiUtil.addApplyPathAction(hlintAutoFindButton, hlintPathField, "hlint")

  val stylishPathField = new TextFieldWithBrowseButton
  val stylishPathErrorsField = new ErrorLabel
  val stylishAutoFindButton = new JButton("Auto Find")
  val stylishSetDefault = new JCheckBox("Set as default")
  GuiUtil.addFolderListener(stylishPathField, "stylish-haskell")
  GuiUtil.addApplyPathAction(stylishAutoFindButton, stylishPathField, "stylish-haskell")

  val contentPane = new JPanel(new GridBagLayout) {
    val gc = GC.pad(10, 5).northWest

    var gridY = 0
    add(new JLabel("GHC Mod:"), gc.grid(0, gridY))
    add(ghcModPathField, gc.fillHorizontal.grid(1, gridY))
    add(ghcModAutoFindButton, gc.grid(2, gridY))
    add(ghcModSetDefault, gc.grid(3, gridY))
    gridY += 1
    add(ghcModPathErrorsField, gc.fillHorizontal.grid(1, gridY))

    gridY += 1
    add(new JLabel("HLint:"), gc.grid(0, gridY))
    add(hlintPathField, gc.fillHorizontal.grid(1, gridY))
    add(hlintAutoFindButton, gc.grid(2, gridY))
    add(hlintSetDefault, gc.grid(3, gridY))
    gridY += 1
    add(hlintPathErrorsField, gc.fillHorizontal.grid(1, gridY))

    gridY += 1
    add(new JLabel("Stylish Haskell:"), gc.grid(0, gridY))
    add(stylishPathField, gc.fillHorizontal.grid(1, gridY))
    add(stylishAutoFindButton, gc.grid(2, gridY))
    add(stylishSetDefault, gc.grid(3, gridY))
    gridY += 1
    add(stylishPathErrorsField, gc.fillHorizontal.grid(1, gridY))

    gridY += 1
    add(new Spacer, gc.width(2).weight(1, 1).grid(0, gridY))
  }

  // Populate from wizardContext
  private val project = Option(wizardContext.getProject).getOrElse(
    ProjectManager.getInstance.getDefaultProject
  )
  ghcModPathField.setText(ToolKey.GHC_MOD_KEY.getPath(project))
  hlintPathField.setText(ToolKey.HLINT_KEY.getPath(project))
  stylishPathField.setText(ToolKey.STYLISH_HASKELL_KEY.getPath(project))
}

case class HaskellBuildToolStep(
  moduleBuilder: HaskellModuleBuilder,
  wizardContext: WizardContext
) extends ModuleWizardStep {

  val form = new HaskellBuildToolStepForm(wizardContext)

  override def getComponent: JComponent = form.contentPane

  override def updateDataModel(): Unit = {
    moduleBuilder.registerSetupRootModelCallback { rootModel: ModifiableRootModel =>
      val project = rootModel.getProject
      val buildSettings = HaskellBuildSettings.getInstance(project)
      if (form.buildWithStackRadio.isSelected) {
        buildSettings.setUseStack(true)
        buildSettings.setStackPath(form.stackPathField.getText)
      } else if (form.buildWithCabalRadio.isSelected) {
        buildSettings.setUseCabal(true)
        buildSettings.setGhcPath(form.ghcPathField.getText)
        buildSettings.setCabalPath(form.cabalPathField.getText)
      } else {
        throw new RuntimeException("Expected Stack or Cabal build.")
      }
    }
  }

  override def validate(): Boolean = {
    // Clear any existing errors.
    List(
      form.stackPathErrorsField,
      form.ghcPathErrorsField,
      form.cabalPathErrorsField
    ).foreach { _.setText("") }

    var result = true
    if (form.buildWithStackRadio.isSelected) {
      if (!new File(form.stackPathField.getText).canExecute) {
        form.stackPathErrorsField.setErrorText("Invalid stack path", Color.red)
        result = false
      }
    } else if (form.buildWithCabalRadio.isSelected) {
      if (!new File(form.ghcPathField.getText).canExecute) {
        form.ghcPathErrorsField.setErrorText("Invalid ghc path", Color.red)
        result = false
      }
      if (!new File(form.cabalPathField.getText).canExecute) {
        form.cabalPathErrorsField.setErrorText("Invalid cabal path", Color.red)
        result = false
      }
    } else {
      form.stackPathErrorsField.setErrorText("Must select Stack or Cabal build.", Color.red)
      result = false
    }
    result
  }
}

class HaskellBuildToolStepForm(wizardContext: WizardContext) {
  val buildWithRadioGroup = new ButtonGroup
  val buildWithStackRadio = new JRadioButton("Build with Stack")
  val buildWithCabalRadio = new JRadioButton("Build with Cabal")
  buildWithRadioGroup.add(buildWithStackRadio)
  buildWithRadioGroup.add(buildWithCabalRadio)
  val stackPathField = new TextFieldWithBrowseButton
  val stackPathErrorsField = new ErrorLabel()
  GuiUtil.addFolderListener(stackPathField, "stack")
  val ghcPathField = new TextFieldWithBrowseButton
  val ghcPathErrorsField = new ErrorLabel()
  GuiUtil.addFolderListener(ghcPathField, "ghc")
  val cabalPathField = new TextFieldWithBrowseButton
  val cabalPathErrorsField = new ErrorLabel()
  GuiUtil.addFolderListener(cabalPathField, "cabal")

  private val stackFields = List(stackPathField)
  private val cabalFields = List(ghcPathField, cabalPathField)

  // Toggle fields enabled by build tool selected.
  buildWithStackRadio.addActionListener { e: ActionEvent =>
    stackFields.foreach { _.setEnabled(true) }
    cabalFields.foreach { _.setEnabled(false) }
  }
  buildWithCabalRadio.addActionListener { e: ActionEvent =>
    stackFields.foreach { _.setEnabled(false) }
    cabalFields.foreach { _.setEnabled(true) }
  }

  val contentPane = new JPanel(new GridBagLayout) {
    val gc = GC.pad(10, 5).northWest

    var gridY = 0
    add(buildWithStackRadio, gc.width(2).weight(1, 0).grid(0, gridY))

    gridY += 1
    add(new JLabel("Stack path:"), gc.grid(0, gridY))
    add(stackPathField, gc.fillHorizontal.grid(1, gridY))
    gridY += 1
    add(stackPathErrorsField, gc.fillHorizontal.grid(1, gridY))

    gridY += 1
    add(buildWithCabalRadio, gc.width(2).weight(x = 1).grid(0, gridY))

    gridY += 1
    add(new JLabel("GHC path:"), gc.grid(0, gridY))
    add(ghcPathField, gc.fillHorizontal.grid(1, gridY))
    gridY += 1
    add(ghcPathErrorsField, gc.fillHorizontal.grid(1, gridY))

    gridY += 1
    add(new JLabel("Cabal path:"), gc.grid(0, gridY))
    add(cabalPathField, gc.fillHorizontal.grid(1, gridY))
    gridY += 1
    add(cabalPathErrorsField, gc.fillHorizontal.grid(1, gridY))

    gridY += 1
    add(new Spacer, gc.grid(0, gridY).weight(y = 1))
  }

  // Populate from wizardContext
  private val project = Option(wizardContext.getProject).getOrElse(
    ProjectManager.getInstance.getDefaultProject
  )
  private val buildSettings = HaskellBuildSettings.getInstance(project)
  // Select the appropriate radio button, defaulting to Stack, and disable the other's fields.
  if (buildSettings.isCabalEnabled) {
    buildWithCabalRadio.setSelected(true)
    stackFields.foreach { _.setEnabled(false) }
  } else {
    buildWithStackRadio.setSelected(true)
    cabalFields.foreach { _.setEnabled(false) }
  }
  stackPathField.setText(buildSettings.getStackPath)
  ghcPathField.setText(buildSettings.getGhcPath)
  cabalPathField.setText(buildSettings.getCabalPath)
}

case class HaskellCabalPackageSettingsStep(
  moduleBuilder: HaskellModuleBuilder,
  wizardContext: WizardContext
) extends ModuleWizardStep with Logging {

  val form = new NewCabalProjectForm

  override def getComponent: JComponent = form.getContentPane

  override def updateDataModel(): Unit = {
    moduleBuilder.registerSetupRootModelCallback { rootModel: ModifiableRootModel =>
      val project = rootModel.getProject
      if (wizardContext.isCreatingNewProject && form.shouldInitializeCabalPackage) {
        val baseDir = project.getBasePath
        val name = project.getName
        createCabalFile(baseDir, name)
        createSetupFile(baseDir)
        if (wizardContext.isCreatingNewProject) {
          // Initialize the project and create the stack.yaml file.
          runStackInitIfEnabled(project)
          val buildSettings = HaskellBuildSettings.getInstance(project)
          buildSettings.setStackFile(FileUtil.join(project.getBasePath, "stack.yaml"))
        }
      }
    }
  }

  private def runStackInitIfEnabled(project: Project): Unit = {
    val buildSettings = HaskellBuildSettings.getInstance(project)
    if (buildSettings.isStackEnabled) {
      val command = new GeneralCommandLine(buildSettings.getStackPath, "init")
      command.withWorkDirectory(project.getBasePath)
      try {
        command.createProcess()
      } catch {
        case e@(_: ExecutionException | _: IOException) =>
          LOG.error("Error when running `stack init`", e)
      }
    }
  }

  private def createSetupFile(baseDir: String): Unit = {
    val newSetupFile = new File(baseDir, "Setup.hs")
    if (newSetupFile.exists()) {
      LOG.warn(s"File '${newSetupFile.getAbsolutePath}' already exists, skipping")
      return
    }
    val writer = new PrintWriter(newSetupFile, "UTF-8")
    writer.println(dedent("""
      import Distribution.Simple
      main = defaultMain
    """))
    writer.close()
  }

  private def createCabalFile(baseDir: String, name: String): Unit = {
    val newCabalFile = new File(baseDir, name + ".cabal")
    if (newCabalFile.exists()) {
      LOG.warn(s"File '${newCabalFile.getAbsolutePath}' already exists, skipping")
      return
    }
    val writer = new PrintWriter(newCabalFile, "UTF-8")
    writer.println(createCabalFileText(name))
    writer.close()
  }

  private def createCabalFileText(name: String): String = {
    val baseText = dedent(s"""
      name:                 $name
      version:              ${form.packageVersionField.getText}
      synopsis:             ${form.synopsisField.getText}
      -- description:
      -- license:
      -- license-file:
      homepage:             ${form.homepageField.getText}
      author:               ${form.authorNameField.getText}
      maintainer:           ${form.maintainerEmailField.getText}
      category:             ${form.categoryField.getSelectedItem}
      -- copyright:
      build-type:           Simple
      -- extra-source-files:
      cabal-version:        ${form.cabalVersionField.getText}
    """)

    val componentHeader = form.componentTypeField.getSelectedItem match {
      case CabalComponentType.Library =>
        dedent("""
          library
            -- exposed-modules:
        """)
      case CabalComponentType.Executable =>
        dedent(s"""
          executable $name
            main-is:              Main.hs
        """)
    }
    val componentText = dedent(s"""
      $componentHeader
        -- other-modules:
        -- other-extensions:
        build-depends:        base >= 4.7 && < 5
        hs-source-dirs:       ${form.sourceDirField.getText}
        default-language:     ${form.languageField.getSelectedItem}
    """)

    dedent(s"""
      $baseText

      $componentText
    """)
  }
}

case class HaskellModifiedSettingsStep(
  moduleBuilder: HaskellModuleBuilder,
  settingsStep: SettingsStep
) extends ModuleWizardStep {

  init()

  // Not needed by the module builder.
  override def getComponent: JComponent = null

  override def updateDataModel(): Unit = {}

  private def init(): Unit = {
    setCompilerOutputDir()
    setSdk()
  }

  private def setSdk(): Unit = {
    settingsStep.getContext.setProjectJdk(HaskellSdkType.findOrCreateSdk())
  }

  private def setCompilerOutputDir(): Unit = {
    val c = settingsStep.getContext
    if (c.isCreatingNewProject && c.isProjectFileDirectorySet) {
      c.setCompilerOutputDirectory(new File(c.getProjectFileDirectory, "dist").getPath)
    }
  }
}
