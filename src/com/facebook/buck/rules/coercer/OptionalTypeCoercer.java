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

package com.facebook.buck.rules.coercer;

import com.facebook.buck.core.cell.nameresolver.CellNameResolver;
import com.facebook.buck.core.model.TargetConfiguration;
import com.facebook.buck.core.path.ForwardRelativePath;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;
import java.util.Optional;
import javax.annotation.Nullable;

/** Coerce to {@link java.util.Optional}. */
public class OptionalTypeCoercer<T> implements TypeCoercer<Object, Optional<T>> {

  private final TypeCoercer<Object, T> coercer;
  private final TypeToken<Optional<T>> typeToken;

  public OptionalTypeCoercer(TypeCoercer<Object, T> coercer) {
    Preconditions.checkArgument(
        !coercer.getOutputType().getRawType().isAssignableFrom(Optional.class),
        "Nested optional fields are ambiguous.");
    this.coercer = coercer;
    this.typeToken =
        new TypeToken<Optional<T>>() {}.where(new TypeParameter<T>() {}, coercer.getOutputType());
  }

  @Override
  public TypeToken<Optional<T>> getOutputType() {
    return typeToken;
  }

  @Override
  public boolean hasElementClass(Class<?>... types) {
    return coercer.hasElementClass(types);
  }

  @Override
  public void traverse(CellNameResolver cellRoots, Optional<T> object, Traversal traversal) {
    if (object.isPresent()) {
      coercer.traverse(cellRoots, object.get(), traversal);
    }
  }

  @Override
  public Optional<T> coerce(
      CellNameResolver cellRoots,
      ProjectFilesystem filesystem,
      ForwardRelativePath pathRelativeToProjectRoot,
      TargetConfiguration targetConfiguration,
      TargetConfiguration hostConfiguration,
      Object object)
      throws CoerceFailedException {
    if (object == null || (object instanceof Optional<?> && !((Optional<?>) object).isPresent())) {
      return Optional.empty();
    }
    return Optional.of(
        coercer.coerce(
            cellRoots,
            filesystem,
            pathRelativeToProjectRoot,
            targetConfiguration,
            hostConfiguration,
            object));
  }

  @Nullable
  @Override
  public Optional<T> concat(Iterable<Optional<T>> elements) {
    Iterable<Optional<T>> presentElements = Iterables.filter(elements, Optional::isPresent);

    if (Iterables.isEmpty(presentElements)) {
      return Optional.empty();
    }

    T result = coercer.concat(Iterables.transform(presentElements, Optional::get));

    return result == null ? null : Optional.of(result);
  }
}
