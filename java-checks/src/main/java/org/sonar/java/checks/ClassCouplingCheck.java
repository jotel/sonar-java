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
package org.sonar.java.checks;

import com.google.common.collect.Sets;
import org.sonar.api.rule.RuleKey;
import org.sonar.check.Priority;
import org.sonar.check.Rule;
import org.sonar.check.RuleProperty;
import org.sonar.plugins.java.api.JavaFileScanner;
import org.sonar.plugins.java.api.JavaFileScannerContext;
import org.sonar.plugins.java.api.tree.ArrayTypeTree;
import org.sonar.plugins.java.api.tree.BaseTreeVisitor;
import org.sonar.plugins.java.api.tree.CatchTree;
import org.sonar.plugins.java.api.tree.ClassTree;
import org.sonar.plugins.java.api.tree.ExpressionTree;
import org.sonar.plugins.java.api.tree.IdentifierTree;
import org.sonar.plugins.java.api.tree.InstanceOfTree;
import org.sonar.plugins.java.api.tree.MemberSelectExpressionTree;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.NewArrayTree;
import org.sonar.plugins.java.api.tree.NewClassTree;
import org.sonar.plugins.java.api.tree.ParameterizedTypeTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.plugins.java.api.tree.TypeCastTree;
import org.sonar.plugins.java.api.tree.TypeParameterTree;
import org.sonar.plugins.java.api.tree.UnionTypeTree;
import org.sonar.plugins.java.api.tree.VariableTree;
import org.sonar.plugins.java.api.tree.WildcardTree;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;
import java.util.Stack;

@Rule(
    key = ClassCouplingCheck.RULE_KEY,
    priority = Priority.MAJOR,
    tags = {"brain-overload"})
public class ClassCouplingCheck extends BaseTreeVisitor implements JavaFileScanner {

  public static final String RULE_KEY = "S1200";
  private static final int DEFAULT_MAX = 20;

  @RuleProperty(
      key = "max",
      defaultValue = "" + DEFAULT_MAX)
  public int max = DEFAULT_MAX;

  private final Stack<Set<String>> nesting = new Stack<Set<String>>();
  private Set<String> types;
  private JavaFileScannerContext context;
  private RuleKey ruleKey = RuleKey.of(CheckList.REPOSITORY_KEY, RULE_KEY);

  @Override
  public void scanFile(JavaFileScannerContext context) {
    this.context = context;
    scan(context.getTree());
  }

  @Override
  public void visitClass(ClassTree tree) {
    if (tree.is(Tree.Kind.CLASS) && tree.simpleName() != null) {
      nesting.push(types);
      types = Sets.newHashSet();
    }
    checkTypes(tree.superClass());
    checkTypes(tree.superInterfaces());
    super.visitClass(tree);
    if (tree.is(Tree.Kind.CLASS) && tree.simpleName() != null) {
      if (types.size() > max) {
        context.addIssue(
            tree,
            ruleKey,
            "Split this class into smaller and more specialized ones to reduce its dependencies on other classes from " +
                types.size() + " to the maximum authorized " + max + " or less.");
      }
      types = nesting.pop();
    }
  }

  @Override
  public void visitVariable(VariableTree tree) {
    checkTypes(tree.type());
    super.visitVariable(tree);
  }

  @Override
  public void visitCatch(CatchTree tree) {
    //skip visit catch parameter for backward compatibility
    scan(tree.block());
  }

  @Override
  public void visitTypeCast(TypeCastTree tree) {
    checkTypes(tree.type());
    super.visitTypeCast(tree);
  }

  @Override
  public void visitMethod(MethodTree tree) {
    checkTypes(tree.returnType());
    super.visitMethod(tree);
  }

  @Override
  public void visitTypeParameter(TypeParameterTree typeParameter) {
    checkTypes(typeParameter.bounds());
    checkTypes(typeParameter.identifier());
    super.visitTypeParameter(typeParameter);
  }

  @Override
  public void visitUnionType(UnionTypeTree tree) {
    checkTypes(tree.typeAlternatives());
    super.visitUnionType(tree);
  }

  @Override
  public void visitParameterizedType(ParameterizedTypeTree tree) {
    checkTypes(tree.type());
    checkTypes(tree.typeArguments());
    super.visitParameterizedType(tree);
  }

  @Override
  public void visitNewClass(NewClassTree tree) {
    checkTypes(tree.typeArguments());
    if (tree.identifier().is(Tree.Kind.PARAMETERIZED_TYPE)) {
      scan(tree.enclosingExpression());
      checkTypes(((ParameterizedTypeTree) tree.identifier()).typeArguments());
      scan(tree.typeArguments());
      scan(tree.arguments());
      scan(tree.classBody());
    } else {
      super.visitNewClass(tree);
    }
  }

  @Override
  public void visitWildcard(WildcardTree tree) {
    checkTypes(tree.bound());
    super.visitWildcard(tree);
  }

  @Override
  public void visitArrayType(ArrayTypeTree tree) {
    checkTypes(tree.type());
    super.visitArrayType(tree);
  }

  @Override
  public void visitInstanceOf(InstanceOfTree tree) {
    checkTypes(tree.type());
    super.visitInstanceOf(tree);
  }

  @Override
  public void visitNewArray(NewArrayTree tree) {
    checkTypes(tree.type());
    super.visitNewArray(tree);
  }

  private void checkTypes(List<? extends Tree> types) {
    for (Tree type : types) {
      checkTypes(type);
    }
  }

  private void checkTypes(@Nullable Tree type) {
    if (type == null || types == null) {
      return;
    }
    if (type.is(Tree.Kind.IDENTIFIER)) {
      types.add(((IdentifierTree) type).name());
    } else if (type.is(Tree.Kind.MEMBER_SELECT)) {
      ExpressionTree expr = (ExpressionTree) type;
      while (expr.is(Tree.Kind.MEMBER_SELECT)) {
        MemberSelectExpressionTree mse = (MemberSelectExpressionTree) expr;
        types.add(mse.identifier().name());
        expr = mse.expression();
      }
      if (expr.is(Tree.Kind.IDENTIFIER)) {
        types.add(((IdentifierTree) expr).name());
      }
      types.add(((MemberSelectExpressionTree) type).identifier().name());
    }
  }
}
