/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cloudera.log4j.redactor;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class contains the logic for redacting Strings.
 */
public class StringRedactor {

  private static class PatternReplacement {
    Pattern pattern;
    String replacement;

    public PatternReplacement(String pattern, String replacement) {
      this.pattern = Pattern.compile(pattern);
      this.replacement = replacement;
    }
  }

  private static class MatcherReplacement {
    Matcher matcher;
    String replacement;

    public MatcherReplacement(PatternReplacement pr) {
      matcher = pr.pattern.matcher("");
      replacement = pr.replacement;
    }
  }

  private Map<String, List<PatternReplacement>> prMap
          = new HashMap<String, List<PatternReplacement>>();

  // This <code>ThreadLocal</code> keeps and reuses the Java RegEx
  // <code>Matcher</code>s for all rules, one set per thread because
  // <code>Matcher</code> is not thread safe.
  private ThreadLocal<Map<String, List<MatcherReplacement>>> mrTL =
          new ThreadLocal<Map<String, List<MatcherReplacement>>>() {
            @Override
            protected Map<String, List<MatcherReplacement>> initialValue() {
              Map<String, List<MatcherReplacement>> matcherMap
                      = new HashMap<String, List<MatcherReplacement>>();
              for (Map.Entry<String, List<PatternReplacement>> entry
                      : prMap.entrySet()) {
                List<MatcherReplacement> list = new ArrayList<MatcherReplacement>();
                matcherMap.put(entry.getKey(), list);
                for (PatternReplacement pr : entry.getValue()) {
                  list.add(new MatcherReplacement(pr));
                }
              }
              return matcherMap;
            }
          };

  /**
   * Given one rule in (hopefully) trigger::regex::mask form, parse it
   * and throw errors if needed.
   * @param sr The StringRedactor to parse the rule into
   * @param rule The line to parse
   * @param file The file from whence it came (or if no file, null)
   * @param lineNo The line in file, if file is non-null.
   * @throws IllegalArgumentException
   */
  private static void parseOneRule(StringRedactor sr, String rule,
                                   String file, int lineNo)
          throws IllegalArgumentException {
    String[] ruleTriple = rule.trim().split("::", 3);
    String where = "";
    if (file != null) {
      where = " at line " + lineNo + " in file " + file;
    }
    if (ruleTriple.length != 3) {
      throw new IllegalArgumentException(
              "Invalid rule" + where + ", it should have 3 parts: " + rule);
    }
    String trigger = ruleTriple[0];
    String regex = ruleTriple[1];
    String mask = ruleTriple[2];

    if (regex.length() == 0) {
      throw new IllegalArgumentException(
              "Invalid rule" + where + ", regex cannot be empty: " + rule);
    }
    List<PatternReplacement> list = sr.prMap.get(trigger);
    if (list == null) {
      list = new ArrayList<PatternReplacement>();
      sr.prMap.put(trigger, list);
    }
    list.add(new PatternReplacement(regex, mask));
  }

  /** Private constructor to prevent external instantiation */
  private StringRedactor() {}

  /**
   * Create a StringRedactor based on the rules in a file.  The file is
   * expected to have one rule per line, where each rule is in the form
   * trigger::regex::mask.  See the README for further details.
   * @param filename The file containing rules for redaction.
   * @return A new instance of a StringRedactor.
   * @throws IllegalArgumentException
   */
  public static StringRedactor createFromFile (String filename)
          throws IllegalArgumentException {
    StringRedactor sr = new StringRedactor();
    int lineNo = 0;
    try {
      BufferedReader in = new BufferedReader(new FileReader(filename));
      try {
        String line;
        while ((line = in.readLine()) != null) {
          parseOneRule(sr, line.trim(), filename, lineNo);
          lineNo++;
        }
      } finally {
        in.close();
      }
    } catch (FileNotFoundException e) {
      throw new IllegalArgumentException("Invalid path in rule", e);
    } catch (IOException e) {
      throw new IllegalArgumentException("Bad input in rule file at line " +
              lineNo, e);
    }
    return sr;
  }

  /**
   * Create a StringRedactor based on the rules contained on one line. Each
   * rule in the line is separated by ||. The rules themselves are in the
   * form trigger::regex::mask.  See the README for further details.
   * @param rules A || separated list of rules.
   * @return A new instance of a StringRedactor.
   * @throws IllegalArgumentException
   */
  public static StringRedactor createFromString (String rules)
          throws IllegalArgumentException {
    StringRedactor sr = new StringRedactor();
    for (String rule : rules.trim().split("\\|\\|")) {
      parseOneRule(sr, rule, null, 0);
    }
    return sr;
  }

  private boolean hasTrigger(String trigger, String msg) {
    //TODO use Boyer-More to make it more efficient
    //TODO http://www.cs.utexas.edu/users/moore/publications/fstrpos.pdf
    return msg.contains(trigger);
  }

  /**
   * The actual redaction - given a message, look through the list of
   * redaction rules and apply if matching. If so, return the redacted
   * String, else return null.
   * @param msg The message to examine.
   * @return The redacted message, if anything changed.
   */
  public String redact(String msg) {
    boolean matched = false;
    for (Map.Entry<String, List<MatcherReplacement>> entry
            : mrTL.get().entrySet()) {
      String key = entry.getKey();
      for (MatcherReplacement mr : entry.getValue()) {
        if (hasTrigger(key, msg)) {
          mr.matcher.reset(msg);
          if (mr.matcher.find()) {
            msg = mr.matcher.replaceAll(mr.replacement);
            matched = true;
          }
        }
      }
    }
    return (matched) ? msg : null;
  }
}
