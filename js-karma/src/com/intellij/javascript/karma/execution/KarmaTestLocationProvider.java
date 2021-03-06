package com.intellij.javascript.karma.execution;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.javascript.testFramework.JsTestFileByTestNameIndex;
import com.intellij.javascript.testFramework.jasmine.JasmineFileStructure;
import com.intellij.javascript.testFramework.jasmine.JasmineFileStructureBuilder;
import com.intellij.javascript.testFramework.qunit.DefaultQUnitModuleStructure;
import com.intellij.javascript.testFramework.qunit.QUnitFileStructure;
import com.intellij.javascript.testFramework.qunit.QUnitFileStructureBuilder;
import com.intellij.javascript.testFramework.util.EscapeUtils;
import com.intellij.lang.javascript.psi.JSFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
* @author Sergey Simonchik
*/
public class KarmaTestLocationProvider implements SMTestLocator {
  private static final String PROTOCOL_ID__CONFIG_FILE = "config";
  private static final String PROTOCOL_ID__TEST_SUITE = "suite";
  private static final String PROTOCOL_ID__TEST = "test";

  public static final KarmaTestLocationProvider INSTANCE = new KarmaTestLocationProvider();

  @NotNull
  @Override
  public List<Location> getLocation(@NotNull String protocol, @NotNull String path, @NotNull Project project, @NotNull GlobalSearchScope scope) {
    final Location location;
    if (PROTOCOL_ID__CONFIG_FILE.equals(protocol)) {
      location = getConfigLocation(project, path);
    }
    else if (PROTOCOL_ID__TEST_SUITE.equals(protocol)) {
      location = getTestLocation(project, path, true);
    }
    else if (PROTOCOL_ID__TEST.equals(protocol)) {
      location = getTestLocation(project, path, false);
    }
    else {
      location = null;
    }
    if (location != null) {
      return Collections.singletonList(location);
    }
    return Collections.emptyList();
  }

  @Nullable
  private static Location<PsiFile> getConfigLocation(Project project, @NotNull String locationData) {
    VirtualFile virtualFile = VfsUtil.findFileByIoFile(new File(locationData), false);
    if (virtualFile != null && virtualFile.isValid()) {
      PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
      if (psiFile != null && psiFile.isValid()) {
        return PsiLocation.fromPsiElement(psiFile);
      }
    }
    return null;
  }

  @Nullable
  private static Location getTestLocation(Project project, @NotNull String locationData, boolean isSuite) {
    List<String> path = EscapeUtils.split(locationData, '.');
    if (path.isEmpty()) {
      return null;
    }
    final List<String> suiteNames;
    final String testName;
    if (isSuite) {
      suiteNames = path;
      testName = null;
    }
    else {
      suiteNames = path.subList(0, path.size() - 1);
      testName = path.get(path.size() - 1);
    }
    PsiElement psiElement = findJasmineElement(project, suiteNames, testName);
    if (psiElement == null) {
      String moduleName = null;
      if (suiteNames.size() == 0) {
        moduleName = DefaultQUnitModuleStructure.NAME;
      }
      else if (suiteNames.size() == 1) {
        moduleName = suiteNames.get(0);
      }
      if (moduleName != null) {
        psiElement = findQUnitElement(project, moduleName, testName);
      }
    }
    if (psiElement != null) {
      return PsiLocation.fromPsiElement(psiElement);
    }
    return null;
  }

  @Nullable
  private static PsiElement findJasmineElement(Project project, @NotNull List<String> suiteNames, @Nullable String testName) {
    String key = JsTestFileByTestNameIndex.createJasmineKey(suiteNames);
    GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
    List<VirtualFile> jsTestVirtualFiles = JsTestFileByTestNameIndex.findJsTestFilesByNameInScope(key, scope);

    JasmineFileStructureBuilder builder = JasmineFileStructureBuilder.getInstance();
    for (VirtualFile file : jsTestVirtualFiles) {
      PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      if (psiFile instanceof JSFile) {
        JasmineFileStructure jasmineFileStructure = builder.fetchCachedTestFileStructure((JSFile)psiFile);
        PsiElement element = jasmineFileStructure.findPsiElement(suiteNames, testName);
        if (element != null && element.isValid()) {
          return element;
        }
      }
    }

    return null;
  }

  @Nullable
  private static PsiElement findQUnitElement(Project project, @NotNull String moduleName, @Nullable String testName) {
    String key = JsTestFileByTestNameIndex.createQUnitKey(moduleName, testName);
    GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
    List<VirtualFile> jsTestVirtualFiles = JsTestFileByTestNameIndex.findJsTestFilesByNameInScope(key, scope);

    QUnitFileStructureBuilder builder = QUnitFileStructureBuilder.getInstance();
    for (VirtualFile file : jsTestVirtualFiles) {
      PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      if (psiFile instanceof JSFile) {
        QUnitFileStructure qunitFileStructure = builder.fetchCachedTestFileStructure((JSFile)psiFile);
        PsiElement element = qunitFileStructure.findPsiElement(moduleName, testName);
        if (element != null && element.isValid()) {
          return element;
        }
      }
    }

    return null;
  }
}
