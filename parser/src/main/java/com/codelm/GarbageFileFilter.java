package com.codelm;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.Statement;

import java.util.List;
import java.util.regex.Pattern;

public class GarbageFileFilter {
    // This pattern is for filenames to identify hash-like names, which is usually the garbage file
    private static final Pattern HASH_PATTERN = Pattern.compile("^[0-9a-fA-F]{32}_\\d+\\.java$");

    public static boolean isGarbage(String fileName, String javaCode) {
        // 1. Check for hashed filename anomalies
        if (HASH_PATTERN.matcher(fileName).matches()) {
            return true;
        }

        // 2. If content is garbage, filter it out
        return isContentGarbage(javaCode);
    }

    private static boolean isContentGarbage(String javaCode) {
        try {
            Parser parser = new Parser();
            String cleanCode = parser.cleanJavaCode(javaCode);
            String formattedCode = parser.formatJavaCode(cleanCode);
            CompilationUnit cu = StaticJavaParser.parse(formattedCode);
            List<ClassOrInterfaceDeclaration> classes = cu.findAll(ClassOrInterfaceDeclaration.class);
            List<EnumDeclaration> enums = cu.findAll(EnumDeclaration.class);
            return classes.isEmpty() && enums.isEmpty(); // Remove files with empty classes
        } catch (Exception e) {
            return false; // Includes normal files with repo tags at beginning, missing imports etc...
        }
    }
}
