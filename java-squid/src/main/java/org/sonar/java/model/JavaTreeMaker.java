/*
 * SonarQube Java
 * Copyright (C) 2012 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.java.model;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.sonar.sslr.api.AstNode;
import com.sonar.sslr.api.AstNodeType;
import com.sonar.sslr.impl.ast.AstXmlPrinter;
import org.sonar.java.ast.api.JavaKeyword;
import org.sonar.java.ast.api.JavaPunctuator;
import org.sonar.java.ast.api.JavaTokenType;
import org.sonar.java.ast.parser.ArgumentListTreeImpl;
import org.sonar.java.ast.parser.JavaGrammar;
import org.sonar.java.ast.parser.QualifiedIdentifierListTreeImpl;
import org.sonar.java.ast.parser.VariableDeclaratorListTreeImpl;
import org.sonar.java.model.declaration.ClassTreeImpl;
import org.sonar.java.model.declaration.EnumConstantTreeImpl;
import org.sonar.java.model.declaration.MethodTreeImpl;
import org.sonar.java.model.declaration.ModifiersTreeImpl;
import org.sonar.java.model.declaration.VariableTreeImpl;
import org.sonar.java.model.expression.IdentifierTreeImpl;
import org.sonar.java.model.expression.MemberSelectExpressionTreeImpl;
import org.sonar.java.model.expression.NewClassTreeImpl;
import org.sonar.java.model.statement.BlockTreeImpl;
import org.sonar.plugins.java.api.tree.AnnotationTree;
import org.sonar.plugins.java.api.tree.BlockTree;
import org.sonar.plugins.java.api.tree.ClassTree;
import org.sonar.plugins.java.api.tree.CompilationUnitTree;
import org.sonar.plugins.java.api.tree.ExpressionTree;
import org.sonar.plugins.java.api.tree.IdentifierTree;
import org.sonar.plugins.java.api.tree.ImportTree;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.ModifiersTree;
import org.sonar.plugins.java.api.tree.PrimitiveTypeTree;
import org.sonar.plugins.java.api.tree.StatementTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.plugins.java.api.tree.Tree.Kind;
import org.sonar.plugins.java.api.tree.TypeParameterTree;

import javax.annotation.Nullable;

import java.util.List;

public class JavaTreeMaker {

  // TODO To be replaced by members such as "STATEMENTS"
  public static Kind[] getKindsAssociatedTo(Class<? extends Tree> associatedInterface) {
    List<Kind> result = Lists.newArrayList();
    for (Kind kind : Kind.values()) {
      if (associatedInterface.equals(kind.getAssociatedInterface())) {
        result.add(kind);
      }
    }
    return result.toArray(new Kind[result.size()]);
  }

  public static final Kind[] TYPE_KINDS = ImmutableList.<Kind>builder()
    .add(getKindsAssociatedTo(PrimitiveTypeTree.class))
    .add(Kind.IDENTIFIER, Kind.MEMBER_SELECT, Kind.PARAMETERIZED_TYPE)
    .add(Kind.ARRAY_TYPE)
    .build()
    .toArray(new Kind[0]);
  public static final Kind[] QUALIFIED_EXPRESSION_KINDS = new Kind[] {Kind.IDENTIFIER, Kind.MEMBER_SELECT};

  public static final AstNodeType[] STATEMENTS_KINDS = new ImmutableList.Builder<AstNodeType>()
    .add(
      Kind.EMPTY_STATEMENT,
      Kind.LABELED_STATEMENT,
      Kind.IF_STATEMENT,
      Kind.ASSERT_STATEMENT,
      Kind.SWITCH_STATEMENT,
      Kind.WHILE_STATEMENT,
      Kind.DO_STATEMENT,
      Kind.BREAK_STATEMENT,
      Kind.CONTINUE_STATEMENT,
      Kind.RETURN_STATEMENT,
      Kind.THROW_STATEMENT,
      Kind.SYNCHRONIZED_STATEMENT,
      Kind.EXPRESSION_STATEMENT,
      Kind.FOR_STATEMENT,
      Kind.FOR_EACH_STATEMENT,
      Kind.TRY_STATEMENT)
    .build()
    .toArray(new AstNodeType[0]);

  private final KindMaps kindMaps = new KindMaps();

  public static void checkType(AstNode astNode, AstNodeType... expected) {
    Preconditions.checkArgument(astNode.is(expected), "Unexpected AstNodeType: %s", astNode.getType().toString());
  }

  public IdentifierTree identifier(AstNode astNode) {
    checkType(astNode, JavaTokenType.IDENTIFIER, JavaKeyword.THIS, JavaKeyword.CLASS, JavaKeyword.SUPER);
    return new IdentifierTreeImpl(InternalSyntaxToken.createLegacy(astNode), astNode);
  }

  /*
   * 4. Types, Values and Variables
   */

  public PrimitiveTypeTree basicType(AstNode astNode) {
    checkType(astNode, JavaKeyword.VOID);
    return new JavaTree.PrimitiveTypeTreeImpl(astNode);
  }

  public ExpressionTree referenceType(AstNode astNode) {
    if (astNode instanceof ExpressionTree && ((JavaTree) astNode).isLegacy()) {
      return (ExpressionTree) astNode;
    }

    return referenceType(astNode, 0);
  }

  ExpressionTree referenceType(AstNode astNode, int dimSize) {
    ExpressionTree result = astNode.getFirstChild().is(Kind.PRIMITIVE_TYPE) ? (PrimitiveTypeTree) astNode.getFirstChild() : (ExpressionTree) astNode.getFirstChild();
    return applyDim(result, dimSize + astNode.getChildren(JavaGrammar.DIM).size());
  }

  /*
   * 7.3. Compilation Units
   */

  public CompilationUnitTree compilationUnit(AstNode astNode) {
    checkType(astNode, JavaGrammar.COMPILATION_UNIT);
    ImmutableList.Builder<ImportTree> imports = ImmutableList.builder();
    for (AstNode importNode : astNode.getChildren(JavaGrammar.IMPORT_DECLARATION)) {
      ExpressionTree qualifiedIdentifier = (ExpressionTree) importNode.getFirstChild(QUALIFIED_EXPRESSION_KINDS);
      AstNode astNodeQualifiedIdentifier = (AstNode) qualifiedIdentifier;
      // star import : if there is a star then add it as an identifier.
      AstNode nextNextSibling = astNodeQualifiedIdentifier.getNextSibling().getNextSibling();
      if (astNodeQualifiedIdentifier.getNextSibling().is(JavaPunctuator.DOT) && nextNextSibling.is(JavaPunctuator.STAR)) {
        qualifiedIdentifier = new MemberSelectExpressionTreeImpl(
          astNodeQualifiedIdentifier.getNextSibling().getNextSibling(),
          qualifiedIdentifier,
          new IdentifierTreeImpl(InternalSyntaxToken.createLegacy(nextNextSibling), nextNextSibling));
      }

      imports.add(new JavaTree.ImportTreeImpl(
        importNode,
        importNode.hasDirectChildren(JavaKeyword.STATIC),
        qualifiedIdentifier));
    }
    ImmutableList.Builder<Tree> types = ImmutableList.builder();
    for (AstNode typeNode : astNode.getChildren(JavaGrammar.TYPE_DECLARATION)) {
      AstNode declarationNode = typeNode.getFirstChild(
        JavaGrammar.CLASS_DECLARATION,
        JavaGrammar.ENUM_DECLARATION,
        JavaGrammar.INTERFACE_DECLARATION,
        Kind.ANNOTATION_TYPE
        );
      if (declarationNode != null) {
        types.add(typeDeclaration((ModifiersTree) typeNode.getFirstChild(JavaGrammar.MODIFIERS), declarationNode));
      }
    }

    ExpressionTree packageDeclaration = null;
    ImmutableList.Builder<AnnotationTree> packageAnnotations = ImmutableList.builder();
    if (astNode.hasDirectChildren(JavaGrammar.PACKAGE_DECLARATION)) {
      AstNode packageDeclarationNode = astNode.getFirstChild(JavaGrammar.PACKAGE_DECLARATION);
      packageDeclaration = (ExpressionTree) packageDeclarationNode.getFirstChild(QUALIFIED_EXPRESSION_KINDS);
      for (AstNode annotationNode : packageDeclarationNode.getChildren(Kind.ANNOTATION)) {
        packageAnnotations.add((AnnotationTree) annotationNode);
      }
    }
    return new JavaTree.CompilationUnitTreeImpl(
      astNode,
      packageDeclaration,
      imports.build(),
      types.build(),
      packageAnnotations.build());
  }

  public ClassTree typeDeclaration(ModifiersTree modifiers, AstNode astNode) {
    if (astNode.is(JavaGrammar.CLASS_DECLARATION)) {
      return classDeclaration(modifiers, astNode);
    } else if (astNode.is(JavaGrammar.ENUM_DECLARATION)) {
      return enumDeclaration(modifiers, astNode);
    } else if (astNode.is(JavaGrammar.INTERFACE_DECLARATION)) {
      return interfaceDeclaration(modifiers, astNode);
    } else if (astNode.is(Kind.ANNOTATION_TYPE)) {
      // TODO Modifiers
      ClassTreeImpl tree = (ClassTreeImpl) astNode;
      tree.setModifiers(modifiers);
      return tree;
    } else {
      throw new IllegalArgumentException("Unexpected AstNodeType: " + astNode.getType().toString()
        + "\n" + AstXmlPrinter.print(astNode));
    }
  }

  /*
   * 8. Classes
   */

  /**
   * 8.1. Class Declarations
   */
  private ClassTree classDeclaration(ModifiersTree modifiers, AstNode astNode) {
    checkType(astNode, JavaGrammar.CLASS_DECLARATION);
    IdentifierTree simpleName = identifier(astNode.getFirstChild(JavaTokenType.IDENTIFIER));
    AstNode extendsNode = astNode.getFirstChild(JavaKeyword.EXTENDS);
    Tree superClass = extendsNode != null ? (Tree) extendsNode.getNextSibling() : null;
    AstNode implementsNode = astNode.getFirstChild(JavaKeyword.IMPLEMENTS);
    List<Tree> superInterfaces = implementsNode != null ? (List) implementsNode.getNextSibling() : ImmutableList.<ExpressionTree>of();
    return new ClassTreeImpl(
      astNode, Tree.Kind.CLASS,
      modifiers,
      simpleName,
      astNode.hasDirectChildren(JavaGrammar.TYPE_PARAMETERS) ?
        (List<TypeParameterTree>) astNode.getFirstChild(JavaGrammar.TYPE_PARAMETERS) :
        ImmutableList.<TypeParameterTree>of(),
      superClass,
      superInterfaces,
      classBody(astNode.getFirstChild(JavaGrammar.CLASS_BODY)));
  }

  /**
   * 8.1.6. Class Body and Member Declarations
   */
  public List<Tree> classBody(AstNode astNode) {
    checkType(astNode, JavaGrammar.CLASS_BODY, JavaGrammar.ENUM_BODY_DECLARATIONS);
    ImmutableList.Builder<Tree> members = ImmutableList.builder();
    for (AstNode classBodyDeclaration : astNode.getChildren(JavaGrammar.CLASS_BODY_DECLARATION)) {
      ModifiersTreeImpl modifiers = (ModifiersTreeImpl) classBodyDeclaration.getFirstChild(JavaGrammar.MODIFIERS);
      if (classBodyDeclaration.hasDirectChildren(JavaGrammar.MEMBER_DECL)) {
        AstNode memberDeclNode = classBodyDeclaration.getFirstChild(JavaGrammar.MEMBER_DECL);
        if (memberDeclNode.hasDirectChildren(JavaGrammar.FIELD_DECLARATION)) {
          members.addAll(fieldDeclaration(
            modifiers,
            memberDeclNode.getFirstChild(JavaGrammar.FIELD_DECLARATION)
            ));
        } else {
          members.add(memberDeclaration(modifiers, memberDeclNode));
        }
      } else if (classBodyDeclaration.getFirstChild().is(JavaGrammar.CLASS_INIT_DECLARATION)) {
        AstNode classInitDeclarationNode = classBodyDeclaration.getFirstChild();

        BlockTreeImpl block = (BlockTreeImpl) classInitDeclarationNode.getFirstChild(Kind.BLOCK);

        members.add(new BlockTreeImpl(
          classInitDeclarationNode,
          classInitDeclarationNode.hasDirectChildren(JavaKeyword.STATIC) ? Tree.Kind.STATIC_INITIALIZER : Tree.Kind.INITIALIZER,
          block));
      }
    }
    return members.build();
  }

  /**
   * 8.2. Class Members
   */
  private Tree memberDeclaration(ModifiersTree modifiers, AstNode astNode) {
    checkType(astNode, JavaGrammar.MEMBER_DECL);
    AstNode declaration = astNode.getFirstChild(
      JavaGrammar.INTERFACE_DECLARATION,
      JavaGrammar.CLASS_DECLARATION,
      JavaGrammar.ENUM_DECLARATION,
      Kind.ANNOTATION_TYPE);
    if (declaration != null) {
      return typeDeclaration(modifiers, declaration);
    }
    declaration = astNode.getFirstChild(JavaGrammar.GENERIC_METHOD_OR_CONSTRUCTOR_REST);
    if (declaration != null) {
      AstNode typeAstNode = declaration.getFirstChild(TYPE_KINDS);
      if (typeAstNode == null) {
        typeAstNode = declaration.getFirstChild(JavaKeyword.VOID);
      }

      return methodDeclarator(
        modifiers,
        astNode.hasDirectChildren(JavaGrammar.TYPE_PARAMETERS) ?
          (List<TypeParameterTree>) astNode.getFirstChild(JavaGrammar.TYPE_PARAMETERS) :
          ImmutableList.<TypeParameterTree>of(),
        /* type */typeAstNode,
        /* name */declaration.getFirstChild(JavaTokenType.IDENTIFIER),
        declaration.getFirstChild(JavaGrammar.METHOD_DECLARATOR_REST, JavaGrammar.CONSTRUCTOR_DECLARATOR_REST));
    }
    declaration = astNode.getFirstChild(
      JavaGrammar.METHOD_DECLARATOR_REST,
      JavaGrammar.VOID_METHOD_DECLARATOR_REST,
      JavaGrammar.CONSTRUCTOR_DECLARATOR_REST
      );
    if (declaration != null) {
      AstNode typeAstNode = astNode.getFirstChild(TYPE_KINDS);
      if (typeAstNode == null) {
        typeAstNode = astNode.getFirstChild(JavaKeyword.VOID);
      }

      return methodDeclarator(
        modifiers,
        /* type */typeAstNode,
        /* name */astNode.getFirstChild(JavaTokenType.IDENTIFIER),
        declaration);
    }
    throw new IllegalStateException();
  }

  /**
   * 8.3. Field Declarations
   */
  private List<StatementTree> fieldDeclaration(ModifiersTreeImpl modifiers, AstNode astNode) {
    checkType(astNode, JavaGrammar.FIELD_DECLARATION);

    Tree type = (Tree) astNode.getFirstChild();
    VariableDeclaratorListTreeImpl variables = (VariableDeclaratorListTreeImpl) astNode.getFirstChild(JavaGrammar.VARIABLE_DECLARATORS);

    for (VariableTreeImpl variable : variables) {
      variable.completeModifiersAndType(modifiers, type);
    }

    return (List) variables;
  }

  /**
   * 8.4. Method Declarations
   */
  private MethodTree methodDeclarator(ModifiersTree modifiers, @Nullable AstNode returnTypeNode, AstNode name, AstNode astNode) {
    return methodDeclarator(modifiers, ImmutableList.<TypeParameterTree>of(), returnTypeNode, name, astNode);
  }

  private MethodTree methodDeclarator(ModifiersTree modifiers, List<TypeParameterTree> typeParameters, @Nullable AstNode returnTypeNode, AstNode name, AstNode astNode) {
    checkType(name, JavaTokenType.IDENTIFIER);
    checkType(astNode, JavaGrammar.METHOD_DECLARATOR_REST,
      JavaGrammar.VOID_METHOD_DECLARATOR_REST,
      JavaGrammar.CONSTRUCTOR_DECLARATOR_REST,
      JavaGrammar.VOID_INTERFACE_METHOD_DECLARATORS_REST,
      JavaGrammar.INTERFACE_METHOD_DECLARATOR_REST);

    Tree returnType = null;
    if (returnTypeNode != null) {
      if (returnTypeNode.is(JavaKeyword.VOID)) {
        returnType = basicType(returnTypeNode);
      } else {
        returnType = applyDim((ExpressionTree) returnTypeNode, astNode.getChildren(JavaGrammar.DIM).size());
      }
    }
    BlockTree body = null;
    if (astNode.hasDirectChildren(JavaGrammar.METHOD_BODY)) {
      body = (BlockTree) astNode.getFirstChild(JavaGrammar.METHOD_BODY).getFirstChild(Kind.BLOCK);
    }
    QualifiedIdentifierListTreeImpl throwsClauseNode = (QualifiedIdentifierListTreeImpl) astNode.getFirstChild(JavaGrammar.QUALIFIED_IDENTIFIER_LIST);
    return new MethodTreeImpl(
      astNode,
      modifiers,
      typeParameters,
      returnType,
      identifier(name),
      (List) astNode.getFirstChild(JavaGrammar.FORMAL_PARAMETERS),
      body,
      throwsClauseNode != null ? throwsClauseNode : ImmutableList.<ExpressionTree>of(),
      null);
  }

  /**
   * 8.9. Enums
   */
  private ClassTree enumDeclaration(ModifiersTree modifiers, AstNode astNode) {
    checkType(astNode, JavaGrammar.ENUM_DECLARATION);
    IdentifierTree enumType = identifier(astNode.getFirstChild(JavaTokenType.IDENTIFIER));
    ImmutableList.Builder<Tree> members = ImmutableList.builder();
    AstNode enumBodyNode = astNode.getFirstChild(JavaGrammar.ENUM_BODY);
    AstNode enumConstantsNode = enumBodyNode.getFirstChild(JavaGrammar.ENUM_CONSTANTS);
    if (enumConstantsNode != null) {
      for (AstNode enumConstantNode : enumConstantsNode.getChildren(JavaGrammar.ENUM_CONSTANT)) {
        AstNode argumentsNode = enumConstantNode.getFirstChild(JavaGrammar.ARGUMENTS);
        AstNode classBodyNode = enumConstantNode.getFirstChild(JavaGrammar.CLASS_BODY);
        IdentifierTree enumIdentifier = identifier(enumConstantNode.getFirstChild(JavaTokenType.IDENTIFIER));
        members.add(new EnumConstantTreeImpl(
          enumConstantNode,
          ModifiersTreeImpl.EMPTY,
          enumType,
          enumIdentifier,
          new NewClassTreeImpl(
            enumConstantNode,
            /* enclosing expression: */null,
            enumIdentifier,
            argumentsNode != null ? (ArgumentListTreeImpl) argumentsNode : ImmutableList.<ExpressionTree>of(),
            classBodyNode == null ? null : new ClassTreeImpl(
              classBodyNode,
              Tree.Kind.CLASS,
              ModifiersTreeImpl.EMPTY,
              classBody(classBodyNode)
              )
          )
          ));
      }
    }
    AstNode enumBodyDeclarationsNode = enumBodyNode.getFirstChild(JavaGrammar.ENUM_BODY_DECLARATIONS);
    if (enumBodyDeclarationsNode != null) {
      members.addAll(classBody(enumBodyDeclarationsNode));
    }
    AstNode implementsNode = astNode.getFirstChild(JavaKeyword.IMPLEMENTS);
    List<Tree> superInterfaces = implementsNode != null ? (List) implementsNode.getNextSibling() : ImmutableList.<Tree>of();
    return new ClassTreeImpl(astNode, Tree.Kind.ENUM, modifiers, enumType, ImmutableList.<TypeParameterTree>of(), /* super class: */null, superInterfaces, members.build());
  }

  /*
   * 9. Interfaces
   */

  /**
   * 9.1. Interface Declarations
   */
  private ClassTree interfaceDeclaration(ModifiersTree modifiers, AstNode astNode) {
    checkType(astNode, JavaGrammar.INTERFACE_DECLARATION);
    IdentifierTree simpleName = identifier(astNode.getFirstChild(JavaTokenType.IDENTIFIER));
    ImmutableList.Builder<Tree> members = ImmutableList.builder();
    for (AstNode interfaceBodyDeclarationNode : astNode.getFirstChild(JavaGrammar.INTERFACE_BODY).getChildren(JavaGrammar.INTERFACE_BODY_DECLARATION)) {
      ModifiersTree memberModifiers = (ModifiersTree) interfaceBodyDeclarationNode.getFirstChild(JavaGrammar.MODIFIERS);
      AstNode interfaceMemberDeclNode = interfaceBodyDeclarationNode.getFirstChild(JavaGrammar.INTERFACE_MEMBER_DECL);
      if (interfaceMemberDeclNode != null) {
        appendInterfaceMember(memberModifiers, members, interfaceMemberDeclNode);
      }
    }
    AstNode extendsNode = astNode.getFirstChild(JavaKeyword.EXTENDS);
    List<Tree> superInterfaces = extendsNode != null ? (List) extendsNode.getNextSibling() : ImmutableList.<Tree>of();
    return new ClassTreeImpl(
      astNode, Tree.Kind.INTERFACE,
      modifiers,
      simpleName,
      astNode.hasDirectChildren(JavaGrammar.TYPE_PARAMETERS) ?
        (List<TypeParameterTree>) astNode.getFirstChild(JavaGrammar.TYPE_PARAMETERS) :
        ImmutableList.<TypeParameterTree>of(),
      null, superInterfaces, members.build());
  }

  /**
   * 9.1.4. Interface Body and Member Declarations
   */
  private void appendInterfaceMember(ModifiersTree modifiers, ImmutableList.Builder<Tree> members, AstNode astNode) {
    checkType(astNode, JavaGrammar.INTERFACE_MEMBER_DECL);
    AstNode declarationNode = astNode.getFirstChild(
      JavaGrammar.INTERFACE_DECLARATION,
      JavaGrammar.CLASS_DECLARATION,
      JavaGrammar.ENUM_DECLARATION,
      Kind.ANNOTATION_TYPE);
    if (declarationNode != null) {
      members.add(typeDeclaration(modifiers, declarationNode));
      return;
    }
    declarationNode = astNode.getFirstChild(JavaGrammar.INTERFACE_METHOD_OR_FIELD_DECL);
    if (declarationNode != null) {
      AstNode interfaceMethodOrFieldRestNode = declarationNode.getFirstChild(JavaGrammar.INTERFACE_METHOD_OR_FIELD_REST);
      AstNode interfaceMethodDeclaratorRestNode = interfaceMethodOrFieldRestNode.getFirstChild(JavaGrammar.INTERFACE_METHOD_DECLARATOR_REST);
      if (interfaceMethodDeclaratorRestNode != null) {
        AstNode typeAstNode = declarationNode.getFirstChild(TYPE_KINDS);
        if (typeAstNode == null) {
          typeAstNode = declarationNode.getFirstChild(JavaKeyword.VOID);
        }

        members.add(methodDeclarator(
          modifiers,
          typeAstNode,
          declarationNode.getFirstChild(JavaTokenType.IDENTIFIER),
          interfaceMethodDeclaratorRestNode
          ));
        return;
      } else {
        appendConstantDeclarations(modifiers, members, declarationNode);
        return;
      }
    }
    declarationNode = astNode.getFirstChild(JavaGrammar.INTERFACE_GENERIC_METHOD_DECL);
    if (declarationNode != null) {
      // TODO TYPE_PARAMETERS
      AstNode typeAstNode = declarationNode.getFirstChild(TYPE_KINDS);
      if (typeAstNode == null) {
        typeAstNode = declarationNode.getFirstChild(JavaKeyword.VOID);
      }

      members.add(methodDeclarator(
        modifiers,
        declarationNode.hasDirectChildren(JavaGrammar.TYPE_PARAMETERS) ?
          (List<TypeParameterTree>) declarationNode.getFirstChild(JavaGrammar.TYPE_PARAMETERS) :
          ImmutableList.<TypeParameterTree>of(),
        /* type */typeAstNode,
        /* name */declarationNode.getFirstChild(JavaTokenType.IDENTIFIER),
        declarationNode.getFirstChild(JavaGrammar.INTERFACE_METHOD_DECLARATOR_REST)
        ));
      return;
    }
    declarationNode = astNode.getFirstChild(JavaGrammar.VOID_INTERFACE_METHOD_DECLARATORS_REST);
    if (declarationNode != null) {
      members.add(methodDeclarator(
        modifiers,
        /* type */astNode.getFirstChild(JavaKeyword.VOID),
        /* name */astNode.getFirstChild(JavaTokenType.IDENTIFIER),
        declarationNode
        ));
      return;
    }
    throw new IllegalStateException();
  }

  public void appendConstantDeclarations(ModifiersTree modifiers, ImmutableList.Builder<Tree> members, AstNode astNode) {
    checkType(astNode, JavaGrammar.INTERFACE_METHOD_OR_FIELD_DECL, JavaGrammar.ANNOTATION_TYPE_ELEMENT_REST);
    AstNode typeAstNode = astNode.getFirstChild(TYPE_KINDS);
    if (typeAstNode == null) {
      typeAstNode = astNode.getFirstChild(JavaKeyword.VOID);
    }
    ExpressionTree type = typeAstNode instanceof Tree ? (ExpressionTree) typeAstNode : referenceType(typeAstNode);
    for (AstNode constantDeclaratorRestNode : astNode.getDescendants(JavaGrammar.CONSTANT_DECLARATOR_REST)) {
      AstNode identifierNode = constantDeclaratorRestNode.getPreviousAstNode();
      Preconditions.checkState(identifierNode.is(JavaTokenType.IDENTIFIER));
      members.add(new VariableTreeImpl(
        constantDeclaratorRestNode,
        modifiers,
        applyDim(type, constantDeclaratorRestNode.getChildren(JavaGrammar.DIM).size()),
        identifier(identifierNode),
        (ExpressionTree) constantDeclaratorRestNode.getLastChild()));
    }
  }

  /*
   * 14. Blocks and Statements
   */

  public List<StatementTree> blockStatements(AstNode astNode) {
    checkType(astNode, JavaGrammar.BLOCK_STATEMENTS);
    ImmutableList.Builder<StatementTree> statements = ImmutableList.builder();
    for (AstNode blockStatementNode : astNode.getChildren(JavaGrammar.BLOCK_STATEMENT)) {
      statements.addAll(blockStatement(blockStatementNode));
    }
    return statements.build();
  }

  public List<StatementTree> blockStatement(AstNode astNode) {
    checkType(astNode, JavaGrammar.BLOCK_STATEMENT);

    AstNode statementNode = astNode.getFirstChild(
      JavaGrammar.VARIABLE_DECLARATORS,
      JavaGrammar.CLASS_DECLARATION,
      JavaGrammar.ENUM_DECLARATION);
    if (statementNode == null && astNode.getNumberOfChildren() == 1) {
      // Statement hack (note that they are not all yet migrated to Kinds)
      statementNode = astNode.getFirstChild();
    }

    if (statementNode instanceof StatementTree && !((JavaTree) statementNode).isLegacy()) {
      return ImmutableList.of((StatementTree) statementNode);
    } else if (statementNode.is(JavaGrammar.VARIABLE_DECLARATORS)) {
      return (List<StatementTree>) statementNode;
    } else if (statementNode.is(JavaGrammar.CLASS_DECLARATION)) {
      return ImmutableList.<StatementTree>of(classDeclaration((ModifiersTree) astNode.getFirstChild(JavaGrammar.MODIFIERS), statementNode));
    } else if (statementNode.is(JavaGrammar.ENUM_DECLARATION)) {
      return ImmutableList.<StatementTree>of(enumDeclaration((ModifiersTree) astNode.getFirstChild(JavaGrammar.MODIFIERS), statementNode));
    } else {
      throw new IllegalStateException("Unexpected AstNodeType: " + statementNode.getType().toString());
    }
  }

  public ExpressionTree applyDim(ExpressionTree expression, int count) {
    ExpressionTree result = expression;
    for (int i = 0; i < count; i++) {
      result = new JavaTree.ArrayTypeTreeImpl(/* FIXME should not be null */null, result);
    }
    return result;
  }

}
