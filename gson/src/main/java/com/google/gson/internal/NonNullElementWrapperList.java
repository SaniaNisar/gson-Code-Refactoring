/*
 * Copyright (C) 2018 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.gson.internal;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.AbstractList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;

/**
 * {@link List} which wraps another {@code List} but prevents inserting {@code null}. Relies on
 * AbstractList for all non-mutating and default bulk operations.
 */
public final class NonNullElementWrapperList<E> extends AbstractList<E> implements RandomAccess {

  private final List<E> delegate;

  /**
   * @param delegate any non-null List that implements RandomAccess
   * @throws NullPointerException if delegate is null
   * @throws IllegalArgumentException if delegate is not RandomAccess
   */
  public NonNullElementWrapperList(List<E> delegate) {
    this.delegate = Objects.requireNonNull(delegate, "delegate must be non-null");
    if (!(delegate instanceof RandomAccess)) {
      throw new IllegalArgumentException("delegate must implement RandomAccess");
    }
  }

  @Override
  public E get(int index) {
    return delegate.get(index);
  }

  @Override
  public int size() {
    return delegate.size();
  }

  @Override
  public E set(int index, E element) {
    E nonNull = requireNonNullElement(element);
    return delegate.set(index, nonNull);
  }

  @Override
  public void add(int index, E element) {
    E nonNull = requireNonNullElement(element);
    delegate.add(index, nonNull);
  }

  @Override
  public E remove(int index) {
    return delegate.remove(index);
  }

  @Override
  public boolean addAll(Collection<? extends E> c) {
    for (E e : c) {
      requireNonNullElement(e);
    }
    return delegate.addAll(c);
  }

  @Override
  public boolean addAll(int index, Collection<? extends E> c) {
    for (E e : c) {
      requireNonNullElement(e);
    }
    return delegate.addAll(index, c);
  }

  /**
   * Centralized null‐check helper. Annotated so callers aren’t forced to use its return (we only
   * care about the side‐effect NullPointerException).
   */
  @CanIgnoreReturnValue
  private static <T> T requireNonNullElement(T element) {
    return Objects.requireNonNull(element, "Element must be non-null");
  }
}
