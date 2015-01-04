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

import org.apache.log4j.Appender;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.rewrite.RewriteAppender;
import org.apache.log4j.spi.LoggingEvent;

import java.util.Enumeration;

/**
 * <code>RewriteAppender</code> that redacts the message of
 * <code>LoggingEvent</code>s and delegates to the referenced
 * <code>Appender</code>.
 * <p/>
 * The redaction rules are enforced by a {@link RedactorPolicy}.
 * <p/>
 * The <code>RedactorAppender</code> configuration is as follows:
 * <p/>
 * <pre>
 * log4j.appender.redactor=com.cloudera.log4j.redactor.RedactorAppender
 * log4j.appender.redactor.appenderRefs=[APPENDERS]
 * log4j.appender.redactor.policy=com.cloudera.log4j.redactor.RedactorPolicy
 * log4j.appender.redactor.policy.rules=[RULES]
 * </pre>
 * <p/>
 * [APPENDERS] should be the list of appenders, comma separated, to wrap for
 * redaction.
 * <p/>
 * All the appenders listed in [APPENDERS] must be added to the rootLogger.
 * <p/>
 * The <code>redactor</code> appender itself must be added to the rootLogger as
 * the last appender.
 * <p/>
 * [RULES] are either
 * <ol>
 *   <li>One big string containing a list of
 *       [TRIGGER]::[REGEX]::[REDACTION_MASK] separated by '||', or</li>
 *   <li>A full path to a file (starts with '/'). This file must contain a
 *       [TRIGGER]::[REGEX]::[REDACTION_MASK] triple on each line.</li>
 * </ol>
 * <p/>
 * If the log message contains the [TRIGGER], the message will be searched
 * for [REGEX] and all occurrences will be replaced with the [REDACTION_MASK].
 * <p/>
 * All rules for which the [TRIGGER] is found will be applied.
 */
public class RedactorAppender extends RewriteAppender {
  private RedactorPolicy policy;
  private String[] appenders;

  /**
   * Log4j configurator calls this with the contents found in the config file.
   */
  public void setPolicy(RedactorPolicy policy) {
    this.policy = policy;
  }

  /**
   * Log4j configurator calls this with the contents found in the config file.
   */
  public void setAppenderRefs(String appenderRefs) {
    this.appenders = appenderRefs.split(",");
  }

  /**
   * For each of the given appenders that are attached to the given logger,
   * place a RedactorAppender "in front of" the real appender so that it can
   * do redaction magic.
   * @param logger The logger to operate on.
   * @param appenders The appenders to wrap.
   */
  private void wrapAppender(Logger logger, String[] appenders) {
    for (String appenderName : appenders) {
      appenderName = appenderName.trim();
      if (!appenderName.isEmpty()) {
        Appender appender = logger.getAppender(appenderName);
        if (appender != null) {
          logger.removeAppender(appenderName);
          RedactorAppender maskingAppender = new RedactorAppender();
          maskingAppender.setRewritePolicy(policy);
          maskingAppender.addAppender(appender);
          logger.addAppender(maskingAppender);
        }
      }
    }
  }

  /**
   * Called after all options are read in so that they can be acted on
   * at one time. Here we wrap all the necessary appenders with
   * RedactorAppender()s.
   */
  @Override
  public void activateOptions() {
    super.activateOptions();
    Enumeration e = LogManager.getCurrentLoggers();
    while (e.hasMoreElements()) {
      Logger logger = (Logger) e.nextElement();
      wrapAppender(logger, appenders);
    }
    wrapAppender(LogManager.getRootLogger(), appenders);
  }

  @Override
  protected void append(LoggingEvent event) {
    super.append(event);
  }
}
