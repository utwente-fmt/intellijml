package nl.utwente.jmlplugin;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

/**
 * Does an integration test on the annotators
 */
public class JMLIntegrationTest extends JavaCodeInsightFixtureTestCase {

    @Override
    protected void tuneFixture(JavaModuleFixtureBuilder<?> moduleBuilder) throws Exception {
        super.tuneFixture(moduleBuilder);
        // a mock JDK that the test framework downloads, so the tests need no intellij-community checkout
        moduleBuilder.addJdkVersion(LanguageLevel.JDK_21);
    }

    @Override
    protected String getTestDataPath() {
        return "src/test/testData/integrationTestData";
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // newer platform versions assert that the language level is changed in a write action
        WriteAction.run(() -> LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_11));
    }

    public void testBox() {
        myFixture.configureByFile("Box.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testJmlComments() {
        myFixture.configureByFile("JmlComments.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testGhostCounter() {
        myFixture.configureByFile("GhostCounter.java");
        myFixture.checkHighlighting(true, false, true, false);
    }

    public void testStorageSpace() {
        myFixture.copyDirectoryToProject(".", "integrationTestData");
        myFixture.allowTreeAccessForFile(myFixture.findFileInTempDir("integrationTestData/Box.java"));
        myFixture.openFileInEditor(myFixture.findFileInTempDir("integrationTestData/StorageSpace.java"));
        myFixture.checkHighlighting(true, false, true, false);
    }

}
