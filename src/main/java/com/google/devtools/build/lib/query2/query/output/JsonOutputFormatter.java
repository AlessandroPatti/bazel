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
package com.google.devtools.build.lib.query2.query.output;

import com.google.common.base.Preconditions;
import com.google.devtools.build.lib.packages.Attribute;
import com.google.devtools.build.lib.packages.Rule;
import com.google.devtools.build.lib.packages.Target;
import com.google.devtools.build.lib.query2.common.CommonQueryOptions;
import com.google.devtools.build.lib.query2.engine.OutputFormatterCallback;
import com.google.devtools.build.lib.query2.engine.QueryEnvironment;
import com.google.devtools.build.lib.query2.engine.SynchronizedDelegatingOutputFormatterCallback;
import com.google.devtools.build.lib.query2.engine.ThreadSafeOutputFormatterCallback;
import com.google.devtools.build.lib.query2.query.aspectresolvers.AspectResolver;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** An output formatter that prints the result as Json. */
class JsonOutputFormatter extends AbstractUnorderedFormatter {

  @Override
  public String getName() {
    return "json";
  }

  @Override
  public ThreadSafeOutputFormatterCallback<Target> createStreamCallback(
      OutputStream out, QueryOptions options, QueryEnvironment<?> env) {
    return new SynchronizedDelegatingOutputFormatterCallback<>(
        createPostFactoStreamCallback(out, options));
  }

  @Override
  public void setOptions(CommonQueryOptions options, AspectResolver aspectResolver) {
    super.setOptions(options, aspectResolver);

    Preconditions.checkArgument(options instanceof QueryOptions);
  }

  @Override
  public OutputFormatterCallback<Target> createPostFactoStreamCallback(
      final OutputStream out, final QueryOptions options) {
    return new OutputFormatterCallback<Target>() {

      private JsonObject result = new JsonObject();
      private Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

      @Override
      public void processOutput(Iterable<Target> partialResult)
          throws IOException, InterruptedException {
        for (Target target : partialResult) {
          result.add(target.getLabel().getCanonicalForm(), createTargetJsonObject(target));
        }
      }

      @Override
      public void close(boolean failFast) throws IOException {
        if (!failFast) {
          out.write(gson.toJson(result).getBytes());
        }
      }
    };
  }

  private static JsonObject createTargetJsonObject(Target target) {
    JsonObject result = new JsonObject();
    result.addProperty("fully_qualified_name", target.getLabel().getCanonicalForm());
    result.addProperty("base_path", target.getLabel().getPackageName());
    if (target instanceof Rule) {
      Rule rule = (Rule) target;
      result.addProperty("class", rule.getRuleClass());
      for (Attribute attr : rule.getAttributes()) {
        PossibleAttributeValues values = PossibleAttributeValues.forRuleAndAttribute(rule, attr);
        if (values.getSource() == AttributeValueSource.RULE) {
          Iterator<Object> it = values.iterator();
          while (it.hasNext()) {
            Object val = it.next();
            result.add(attr.getName(), getJsonFromValue(val));
          }
        }
      }
    }
    return result;
  }

  private static JsonElement getJsonFromValue(Object val) {
    Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    if (val instanceof List) {
      Iterator<Object> it = ((List) val).iterator();
      JsonArray result = new JsonArray();
      while (it.hasNext()) {
        Object currentVal = it.next();
        result.add(getJsonFromValue(currentVal));
      }
      return result;
    } else if (val instanceof Map) {
      JsonObject result = new JsonObject();
      Map<Object, Object> valMap = (Map) val;
      for (Object key : valMap.keySet()) {
        result.add(key.toString(), getJsonFromValue(valMap.get(key)));
      }
      return result;
    }
    else if (val instanceof Boolean)
      return gson.toJsonTree(val);
    return gson.toJsonTree(val.toString());
  }
}
