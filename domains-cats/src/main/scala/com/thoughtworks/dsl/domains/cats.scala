package com.thoughtworks.dsl
package domains

import _root_.cats.FlatMap
import com.thoughtworks.dsl.Dsl
import com.thoughtworks.dsl.Dsl.Instruction
import _root_.cats.MonadError
import com.thoughtworks.dsl.instructions.{Catch, Monadic, Scope}

import scala.language.higherKinds
import scala.language.implicitConversions
import scala.util.control.Exception.Catcher
import scala.util.control.{ControlThrowable, NonFatal}

/**
  * @author 杨博 (Yang Bo)
  */
object cats {

  implicit def scalazScopeDsl[F[_], A, B](implicit monadError: MonadError[F, Throwable]): Dsl[Scope[F[B], A], F[B], A] =
    new Dsl[Scope[F[B], A], F[B], A] {
      def interpret(instruction: Scope[F[B], A], handler: A => F[B]): F[B] = {
        val continuation: (A => F[B]) => F[B] = instruction.continuation
        final case class BreakScope(a: A) extends ControlThrowable
        monadError.handleErrorWith(continuation { a =>
          monadError.raiseError(BreakScope(a))
        }) {
          case BreakScope(a) =>
            handler(a)
          case e: Throwable =>
            monadError.raiseError(e)
        }
      }
    }

  implicit def catsCatchDsl[F[_], A](
      implicit monadError: MonadError[F, Throwable]): Dsl[Catch[F[A]], F[A], F[A] => F[A]] =
    new Dsl[Catch[F[A]], F[A], F[A] => F[A]] {
      def interpret(instruction: Catch[F[A]], continuation: (F[A] => F[A]) => F[A]): F[A] = {
        def exceptionHandler(e: Throwable): F[A] = {
          try {
            instruction.failureHandler(e)
          } catch {
            case NonFatal(rethrown) =>
              monadError.raiseError(rethrown)
          }
        }

        try {
          continuation { fa: F[A] =>
            monadError.handleErrorWith(fa)(exceptionHandler)
          }
        } catch {
          case e: Throwable =>
            exceptionHandler(e)
        }
      }
    }

  implicit def catsFlatMapDsl[F[_], A, B](implicit flatMap: FlatMap[F]): Dsl[Monadic[F, A], F[B], A] =
    new Dsl[Monadic[F, A], F[B], A] {
      def interpret(instruction: Monadic[F, A], handler: A => F[B]): F[B] = {
        flatMap.flatMap(instruction.fa)(handler)
      }
    }

}