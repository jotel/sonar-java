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
package org.sonar.plugins.java.bridges;

import org.sonar.api.checks.CheckFactory;
import org.sonar.api.component.ResourcePerspectives;
import org.sonar.api.issue.Issuable;
import org.sonar.api.issue.Issue;
import org.sonar.api.resources.Resource;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rules.ActiveRule;
import org.sonar.squidbridge.api.CheckMessage;
import org.sonar.squidbridge.api.SourceFile;

import java.util.Set;

public class ChecksBridge {

  private final CheckFactory checkFactory;
  private final ResourcePerspectives resourcePerspectives;

  public ChecksBridge(CheckFactory checkFactory, ResourcePerspectives resourcePerspectives) {
    this.checkFactory = checkFactory;
    this.resourcePerspectives = resourcePerspectives;
  }

  public void reportIssues(SourceFile squidFile, Resource sonarFile) {
    if (squidFile.hasCheckMessages()) {
      Issuable issuable = resourcePerspectives.as(Issuable.class, sonarFile);
      Set<CheckMessage> messages = squidFile.getCheckMessages();
      for (CheckMessage checkMessage : messages) {
        Object check = checkMessage.getCheck();
        RuleKey ruleKey;
        if (check instanceof RuleKey) {
          // VisitorsBridge uses RuleKey
          ruleKey = (RuleKey) check;
        } else {
          ActiveRule rule = checkFactory.getActiveRule(checkMessage.getCheck());
          if (rule == null) {
            // rule not active
            continue;
          }
          ruleKey = rule.getRule().ruleKey();
        }
        Issue issue = issuable.newIssueBuilder()
            .ruleKey(ruleKey)
            .line(checkMessage.getLine())
            .message(checkMessage.formatDefaultMessage()).build();
        issuable.addIssue(issue);
      }
      // Remove from memory:
      messages.clear();
    }
  }

}
