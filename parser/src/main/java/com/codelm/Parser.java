package com.codelm;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.printer.DefaultPrettyPrinter;

public class Parser {

    public Parser() {}

    public String cleanJavaCode(String javaCode) {
        CompilationUnit cu = StaticJavaParser.parse(javaCode);

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

        return cu.toString().replaceAll("\\s+", " ");
    }

    public String formatJavaCode(String cleanCode){
        CompilationUnit cu = StaticJavaParser.parse(cleanCode);
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        return printer.print(cu);
    }
}
