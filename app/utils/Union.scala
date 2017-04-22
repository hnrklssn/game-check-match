package utils

/**
 * Created by Henrik on 2017-04-15.
 */
object Union {
  import scala.language.higherKinds
  sealed trait ¬[-A]
  sealed trait TSet {
    type Compound[A]
    type Map[F[_]] <: TSet
  }
  sealed trait ∅ extends TSet {
    type Compound[A] = A
    type Map[F[_]] = ∅
  } // Note that this type is left-associative for the sake of concision.
  sealed trait ∨[T <: TSet, H] extends TSet {
    // Given a type of the form `∅ ∨ A ∨ B ∨ ...` and parameter `X`, we want to produce the type
    // `¬[A] with ¬[B] with ... <:< ¬[X]`.
    type Member[X] = T#Map[¬]#Compound[¬[H]] <:< ¬[X] // This could be generalized as a fold, but for concision we leave it as is.
    type Compound[A] = T#Compound[H with A]
    type Map[F[_]] = T#Map[F] ∨ F[H]
  }
  def foo[A: (∅ ∨ String ∨ Int ∨ List[Int])#Member](a: A): String = a match { case s: String => "String" case i: Int => "Int" case l: List[_] => "List[Int]" }
  //foo(42) foo ("bar") foo (List(1, 2, 3)) foo (42d) // error foo[Any](???) // error }
}
