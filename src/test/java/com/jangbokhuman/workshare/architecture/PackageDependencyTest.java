package com.jangbokhuman.workshare.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(packages = "com.jangbokhuman.workshare", importOptions = ImportOption.DoNotIncludeTests.class)
class PackageDependencyTest {
    @ArchTest
    static final ArchRule packagesAreFreeOfCycles = slices()
            .matching("com.jangbokhuman.workshare.(**)")
            .should()
            .beFreeOfCycles();

    @ArchTest
    static final ArchRule configDoesNotDependOnBusinessAreas = noClasses()
            .that()
            .resideInAPackage("..workshare.config..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "..workshare.employee..",
                    "..workshare.activity..",
                    "..workshare.attendance..",
                    "..workshare.support..",
                    "..workshare.calendar..",
                    "..workshare.web.."
            );

    @ArchTest
    static final ArchRule businessAreasDoNotDependOnWeb = noClasses()
            .that()
            .resideOutsideOfPackage("..workshare.web..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..workshare.web..");
}
