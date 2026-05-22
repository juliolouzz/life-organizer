package com.julio.lifeorganizer.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.tngtech.archunit.core.domain.JavaModifier.PRIVATE;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

// Enforces ADR-001 / ADR-002 layering and CLAUDE.md hard rules at build time.
@Tag("unit")
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.julio.lifeorganizer");
    }

    @Test
    void persistence_doesNotCallUpward_intoServiceOrWeb() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..persistence..")
                .should().dependOnClassesThat().resideInAnyPackage("..service..", "..web..");
        rule.check(classes);
    }

    @Test
    void service_doesNotDependOnControllers() {
        // Services do depend on DTO records that live in ..web.dto.. - that is intentional
        // per ADR-002. What they MUST NOT depend on is Controllers.
        ArchRule rule = noClasses()
                .that().resideInAPackage("..service..")
                .should().dependOnClassesThat()
                .haveSimpleNameEndingWith("Controller");
        rule.check(classes);
    }

    @Test
    void allDtoClasses_areRecords() {
        ArchRule rule = classes()
                .that().resideInAPackage("..web.dto..")
                .should().beRecords();
        rule.check(classes);
    }

    @Test
    void noFieldsAreAnnotatedWithAutowired() {
        ArchRule rule = noFields()
                .should().beAnnotatedWith(Autowired.class)
                .because("constructor injection only; CLAUDE.md hard rule");
        rule.check(classes);
    }

    @Test
    void serviceFieldsAreFinalAndPrivate() {
        // All instance fields in @Service classes must be final + private (constructor injection).
        ArchRule rule = fields()
                .that().areDeclaredInClassesThat().resideInAPackage("..service..")
                .and().areNotStatic()
                .should().beFinal()
                .andShould().haveModifier(PRIVATE);
        rule.check(classes);
    }

    @Test
    void controllersOnlyDelegateToServices_neverDirectlyAccessRepositories() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..web..")
                .and().haveSimpleNameEndingWith("Controller")
                .should().dependOnClassesThat().resideInAPackage("..persistence..");
        rule.check(classes);
    }
}
