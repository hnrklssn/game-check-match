package utils

import java.time.LocalDateTime

import scala.concurrent.duration.Duration
import scala.concurrent.{ CanAwait, ExecutionContext, Future }
import scala.util.Try

/**
 * Created by henrik on 2017-02-24.
 */
case class TimestampedFuture[T](instance: Future[T], timeOfCreation: LocalDateTime) extends Future[T] with Timestamp {

  override def onComplete[U](func: (Try[T]) => U)(implicit executor: ExecutionContext): Unit = instance.onComplete(func)
  override def isCompleted: Boolean = instance.isCompleted

  override def value: Option[Try[T]] = instance.value

  //@throws[T](classOf[InterruptedException])
  //@throws[T](classOf[TimeoutException])
  override def ready(atMost: Duration)(implicit permit: CanAwait): TimestampedFuture.this.type = {
    instance.ready(atMost)
    this
  }

  //@throws[T](classOf[Exception])
  override def result(atMost: Duration)(implicit permit: CanAwait): T = instance.result(atMost)

  override def transform[S](s: (T) => S, f: (Throwable) => Throwable)(implicit executor: ExecutionContext): TimestampedFuture[S] = TimestampedFuture(instance.transform(s, f), timeOfCreation)

  override def map[S](f: (T) => S)(implicit executor: ExecutionContext): TimestampedFuture[S] = TimestampedFuture(instance.map(f), timeOfCreation)

  override def flatMap[S](f: (T) => Future[S])(implicit executor: ExecutionContext): Future[S] = TimestampedFuture(instance.flatMap(f), timeOfCreation)

  override def filter(p: (T) => Boolean)(implicit executor: ExecutionContext): TimestampedFuture[T] = TimestampedFuture(instance.filter(p), timeOfCreation)

  override def collect[S](pf: PartialFunction[T, S])(implicit executor: ExecutionContext): TimestampedFuture[S] = TimestampedFuture(instance.collect(pf), timeOfCreation)

  override def recover[U >: T](pf: PartialFunction[Throwable, U])(implicit executor: ExecutionContext): TimestampedFuture[U] = TimestampedFuture(instance.recover(pf), timeOfCreation)

  override def recoverWith[U >: T](pf: PartialFunction[Throwable, Future[U]])(implicit executor: ExecutionContext): TimestampedFuture[U] = TimestampedFuture(instance.recoverWith(pf), timeOfCreation)

  //Timestamp of this object kept; if other instance is Timestamped, that timestamp is discarded
  override def zip[U](that: Future[U]): TimestampedFuture[(T, U)] = TimestampedFuture(instance.zip(that), timeOfCreation)

  override def fallbackTo[U >: T](that: Future[U]): TimestampedFuture[U] = TimestampedFuture(instance.fallbackTo(that), timeOfCreation)

  override def mapTo[S](implicit tag: scala.reflect.ClassTag[S]): TimestampedFuture[S] = TimestampedFuture(instance.mapTo(tag), timeOfCreation)

  override def andThen[U](pf: PartialFunction[Try[T], U])(implicit executor: ExecutionContext): TimestampedFuture[T] = TimestampedFuture(instance.andThen(pf), timeOfCreation)

}
object TimestampedFuture {
  def apply[T, A](args: A)(factory: A => T)(implicit executionContext: ExecutionContext): TimestampedFuture[T] = {
    val obj: Future[T] = Future { factory(args) }
    val time = LocalDateTime.now()
    TimestampedFuture(obj, time)
  }
}
