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

package com.facebook.buck.cli;

import com.facebook.buck.core.cell.name.CanonicalCellName;
import com.facebook.buck.core.model.CellRelativePath;
import com.facebook.buck.core.path.ForwardRelativePath;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Class maintaining an index of paths to their descendants
 *
 * <p>NOTE: This is similar in concept, though not in implementation, to BuildFileTree. At some
 * point we may want to merge the two together.
 */
class BuildFileDescendantsIndex {

  // An index linking a path to it's direct children.
  private final ImmutableSetMultimap<CellRelativePath, CellRelativePath> pathToChildrenIndex;

  private BuildFileDescendantsIndex(
      ImmutableSetMultimap<CellRelativePath, CellRelativePath> pathToChildrenIndex) {
    this.pathToChildrenIndex = pathToChildrenIndex;
  }

  /**
   * Constructs a BuildFileDescendantsIndex given the leaf paths of the tree. The resulting index
   * can be queried with any parent of those leaf paths and the output will always return those
   * leaves (plus any intermediate directories).
   */
  public static BuildFileDescendantsIndex createFromLeafPaths(Collection<CellRelativePath> paths) {
    ImmutableSetMultimap.Builder<CellRelativePath, CellRelativePath> result =
        ImmutableSetMultimap.builder();
    HashMap<CanonicalCellName, HashSet<ForwardRelativePath>> seen = new HashMap<>();
    for (CellRelativePath path : paths) {
      CanonicalCellName cellName = path.getCellName();
      HashSet<ForwardRelativePath> seenForCell =
          seen.computeIfAbsent(cellName, _cellName -> new HashSet<>());
      CellRelativePath current = path;
      // We break out of this loop below when `getParent()` returns `null`.
      while (true) {
        ForwardRelativePath pathInCell = current.getPath();
        // Minor optimization, to avoid the case where `//some/deep/path/a` and `//some/deep/path/b`
        // spend most of their time calculating the same things.
        if (seenForCell.contains(pathInCell)) {
          break;
        }

        seenForCell.add(pathInCell);

        ForwardRelativePath parent = pathInCell.getParent();
        if (parent == null) {
          // Base case - `current` is a top level directory (eg `//foo`). We need to make a final
          // link between the root directory and current then break out of the while loop. Since
          // parent is `null` we need to use ForwardRelativePath.EMPTY instead.
          result.put(CellRelativePath.of(cellName, ForwardRelativePath.EMPTY), current);
          break;
        }
        CellRelativePath parentCellRelativePath = CellRelativePath.of(cellName, parent);
        result.put(parentCellRelativePath, current);

        current = parentCellRelativePath;
      }
    }
    return new BuildFileDescendantsIndex(result.build());
  }

  /**
   * Given a path {@code root}, returns the set of all recursive directories in the index under the
   * root directory.
   */
  public ImmutableSet<CellRelativePath> getRecursiveDescendants(CellRelativePath root) {
    HashSet<CellRelativePath> result = new HashSet<>();
    collectRecursiveBuildFiles(result, root);
    return ImmutableSet.copyOf(result);
  }

  private void collectRecursiveBuildFiles(Set<CellRelativePath> result, CellRelativePath path) {
    if (result.contains(path)) {
      return;
    }

    result.add(path);

    for (CellRelativePath child : pathToChildrenIndex.get(path)) {
      collectRecursiveBuildFiles(result, child);
    }
  }
}
