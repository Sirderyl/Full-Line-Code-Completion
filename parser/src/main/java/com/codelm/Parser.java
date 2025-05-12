package com.codelm;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.util.Arrays;
import java.util.regex.Pattern;

public class Parser {

    private static final Pattern JAVA_CODE_START = Pattern.compile(
            "^\\s*(package|import|public|class|interface|enum|@interface).*"
    );

    public Parser() {}

    public String cleanJavaCode(String javaCode) {
        String noPrefixCode = removePrefixLines(javaCode);

        // Calling formatJavaCode as it shifts in-line comments which otherwise would not get picked up by getAllComments()
        CompilationUnit cu = StaticJavaParser.parse(formatJavaCode(noPrefixCode));

        // For in-line comment removal which would not get picked up
        //LexicalPreservingPrinter.setup(cu);

        // Remove comments
        cu.getAllComments().forEach(Comment::remove);

        // Remove annotations
        cu.findAll(Node.class).stream()
                .filter(node -> node instanceof NodeWithAnnotations)
                .forEach(node -> ((NodeWithAnnotations<?>) node).getAnnotations().clear());

        // Remove annotation declarations (e.g., @interface definitions)
        //cu.findAll(AnnotationDeclaration.class).forEach(Node::remove);

        // Remove annotation member declarations
        //cu.findAll(AnnotationMemberDeclaration.class).forEach(Node::remove);

        return cu.toString();
    }

    private String removePrefixLines(String javaCode) {
        String normalizedCode = javaCode.replace("\r\n", "\n");
        normalizedCode = normalizedCode.replace("\r", "\n");

        String[] lines = normalizedCode.split("\n");
        int startIndex = 0;
        for (String line : lines) {
            String trimmedLine = line.trim();
            if (JAVA_CODE_START.matcher(trimmedLine).matches()) {
                break;
            }
            startIndex++;
        }
        return String.join("\n", Arrays.copyOfRange(lines, startIndex, lines.length));
    }

    public String formatJavaCode(String cleanCode){
        CompilationUnit cu = StaticJavaParser.parse(cleanCode);
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        return printer.print(cu);
    }
}
