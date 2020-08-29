/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.facebook.buck.apple;

import com.facebook.buck.core.util.log.Logger;
import com.facebook.buck.cxx.CxxDebugSymbolLinkStrategy;
import com.facebook.buck.cxx.CxxDebugSymbolLinkStrategyFactory;
import com.facebook.buck.rules.args.Arg;
import com.facebook.buck.util.json.ObjectMappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Factory to create {@link AppleCxxDebugSymbolLinkStrategy}. */
public class AppleCxxDebugSymbolLinkStrategyFactory implements CxxDebugSymbolLinkStrategyFactory {

  private final ImmutableSet<String> focusedTargets;
  private static final Logger LOG = Logger.get(AppleCxxDebugSymbolLinkStrategyFactory.class);

  public AppleCxxDebugSymbolLinkStrategyFactory(Optional<Path> focusedTargetsPath) {
    this.focusedTargets =
        focusedTargetsPath
            .map(AppleCxxDebugSymbolLinkStrategyFactory::getFocusedTargets)
            .orElse(ImmutableSet.of());
  }

  public static CxxDebugSymbolLinkStrategyFactory getDebugStrategyFactory(AppleConfig appleConfig) {
    return new AppleCxxDebugSymbolLinkStrategyFactory(appleConfig.getFocusedTargetsPath());
  }

  @Override
  public CxxDebugSymbolLinkStrategy createStrategy(ImmutableList<Arg> linkerArgs) {
    return new AppleCxxDebugSymbolLinkStrategy(focusedTargets, linkerArgs);
  }

  private static ImmutableSet<String> getFocusedTargets(Path focusedTargetsPath) {
    try {
      Map<String, Object> focusedDict =
          ObjectMappers.readValue(
              focusedTargetsPath, new TypeReference<LinkedHashMap<String, Object>>() {});
      @SuppressWarnings("unchecked")
      List<String> focusedTargets = (List<String>) focusedDict.get("targets");
      return ImmutableSet.copyOf(focusedTargets);
    } catch (Exception e) {
      LOG.error(e.getMessage());
      // We'll fail fast here to indicate invalid focus_targets_path. Since this method
      // only runs when apple.focused_targets_path is present, this shouldn't affect other
      // buck work flows.
      throw new RuntimeException("Failed to read from apple.focused_targets_path!", e);
    }
  }
}
