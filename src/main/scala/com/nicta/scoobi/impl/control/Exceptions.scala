/**
 * Copyright 2011,2012 National ICT Australia Limited
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
package com.nicta.scoobi
package impl
package control

import scalaz.Monoid

/**
 * This trait provides methods to catch exceptions and transform them into values which can be passed to
 * further computations.
 *
 * It uses the facilities found in the scala.util.control.Exception object while providing
 * a more concise api on some use cases.
 *
 * @see com.nicta.scoobi.impl.control.ExceptionsSpec for examples
 */
private[scoobi]
trait Exceptions {
  /**
   * try to evaluate an expression, returning an Option
   *
   * The 'tryo' name comes from the lift project: http://liftweb.net
   *
   * @return None if there is an exception, or Some(value)
   */
  def tryo[T](a: =>T): Option[T] = {
    try { Some(a) }
    catch { case e: Exception => None }
  }

  /**
   * try to evaluate an expression, returning a value T
   *
   * If the expression throws an Exception a function f is used to return a value
   * of the expected type.
   */
  def tryOr[T](a: =>T)(implicit handler: Exception => T): T = {
    trye(a)(handler).fold(identity, identity)
  }
  /**
   * try to evaluate an expression, returning a value T
   *
   * If the expression throws a Throwable a function f is used to return a value
   * of the expected type.
   */
  def catchAllOr[T](a: =>T)(f: Throwable =>T): T = {
    try { a }
    catch { case e: Throwable => f(e) }
  }
  /**
   * try to evaluate an expression, returning a value T
   *
   * If the expression throws a Throwable, then return a default value
   */
  def catchAllOrElse[T](a: =>T)(ko: =>T): T = catchAllOr(a)((e: Throwable) => ko)
  /**
   * try to evaluate an expression and return it if nothing fails.
   * return ko otherwise
   */
  def tryOrElse[T](a: =>T)(ko: T): T = tryo(a).getOrElse(ko)

  /**
   * try to evaluate an expression and return it if nothing fails.
   * return the zero value of the Monoid instance otherwise
   */
  def tryOrZero[T : Monoid](a: =>T): T = tryOrElse(a)(implicitly[Monoid[T]].zero)

  /**
   * try to evaluate an expression and return ok if nothing fails.
   * return ko otherwise
   */
  def tryMap[T, S](a: =>T)(ok: S)(ko: S): S =
    trye(a).fold(_ => ko, _ => ok)

  /**
   * try to evaluate an expression and return true if nothing fails.
   * return false otherwise
   */
  def tryOk[T](a: =>T) = tryo(a).isDefined
  /**
   * try to evaluate an expression and return true if there is an exception
   * return false otherwise
   */
  def tryKo[T](a: =>T) = !tryOk(a)
  /**
   * try to evaluate an expression, returning Either
   *
   * If the expression throws an Exception a function f is used to return the left value
   * of the Either returned value.
   */
  def trye[T, S](a: =>T)(implicit f: Exception => S): Either[S, T] = {
    try { Right(a) }
    catch { case e: Exception => Left(f(e)) }
  }
  /**
   * try to evaluate an expression, returning Either
   *
   * If the expression throws any Throwable a function f is used to return the left value
   * of the Either returned value.
   */
  def catchAll[T, S](a: =>T)(f: Throwable => S): Either[S, T] = {
    try { Right(a) }
    catch { case e: Throwable => Left(f(e)) }
  }
}

object Exceptions extends Exceptions
