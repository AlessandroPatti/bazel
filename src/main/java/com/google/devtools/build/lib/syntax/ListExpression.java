// Copyright 2014 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.lib.syntax;

import java.io.IOException;
import java.util.List;

/** Syntax node for list and tuple expressions. */
public final class ListExpression extends Expression {

  // TODO(adonovan): split class into {List,Tuple}Expression, as a tuple may have no parens.
  private final boolean isTuple;
  private final List<Expression> elements;

  ListExpression(boolean isTuple, List<Expression> elements) {
    this.isTuple = isTuple;
    this.elements = elements;
  }

  public List<Expression> getElements() {
    return elements;
  }

  /** Reports whether this is a tuple expression. */
  public boolean isTuple() {
    return isTuple;
  }

  @Override
  public void prettyPrint(Appendable buffer) throws IOException {
    buffer.append(isTuple() ? '(' : '[');
    String sep = "";
    for (Expression e : elements) {
      buffer.append(sep);
      e.prettyPrint(buffer);
      sep = ", ";
    }
    if (isTuple() && elements.size() == 1) {
      buffer.append(',');
    }
    buffer.append(isTuple() ? ')' : ']');
  }

  @Override
  public String toString() {
    // Print [a, b, c, ...] up to a maximum of 4 elements or 32 chars.
    StringBuilder buf = new StringBuilder();
    buf.append(isTuple() ? '(' : '[');
    appendNodes(buf, elements);
    if (isTuple() && elements.size() == 1) {
      buf.append(',');
    }
    buf.append(isTuple() ? ')' : ']');
    return buf.toString();
  }

  // Appends elements to buf, comma-separated, abbreviating if they are numerous or long.
  // (Also used by FuncallExpression.)
  static void appendNodes(StringBuilder buf, List<? extends Node> elements) {
    int n = elements.size();
    for (int i = 0; i < n; i++) {
      if (i > 0) {
        buf.append(", ");
      }
      int mark = buf.length();
      buf.append(elements.get(i));
      // Abbreviate, dropping this element, if we exceed 32 chars,
      // or 4 elements (with more elements following).
      if (buf.length() >= 32 || (i == 4 && i + 1 < n)) {
        buf.setLength(mark);
        // TODO(adonovan): "+%d more" is shorter and better suits ListExpression.
        buf.append(String.format("<%d more arguments>", n - i));
        break;
      }
    }
  }

  @Override
  public void accept(NodeVisitor visitor) {
    visitor.visit(this);
  }

  @Override
  public Expression.Kind kind() {
    return Expression.Kind.LIST_EXPR;
  }
}
