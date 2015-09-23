/*
 * Verifies that rules involving history work
 */

package com.holdenkarau.spark_validator

import com.holdenkarau.spark.testing._

import org.scalatest.{Assertions, BeforeAndAfterEach, FunSuite}
import org.apache.spark.Accumulator
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import java.nio.file.Files;

class HistoricValidationTests extends FunSuite with SharedSparkContext {
  val tempPath = Files.createTempDirectory(null).toString()

  // A simple job we can use for some sanity checking
  def runSimpleJob(sc: SparkContext, acc: Accumulator[Int]) {
    val input = sc.parallelize(1.to(10), 5)
    input.foreach(acc += _)
    import com.google.common.io.Files
    input.saveAsTextFile(Files.createTempDir().toURI().toString()+"/magic")
  }

  test("simple first run test - populating acc") {
    val vc = new ValidationConf(tempPath, "1", true,
            List[ValidationRule](
        new AvgRule("acc", 0, Some(200), newCounter=true)))
    val v = Validation(sc, vc)
    val acc = sc.accumulator(0)
    v.registerAccumulator(acc, "acc")
    runSimpleJob(sc, acc)
    assert(v.validate(1) === true)
  }


  test("sample expected failure - should not be included in historic data") {
    val vc = new ValidationConf(tempPath, "1", true,
      List[ValidationRule](
        new AbsoluteSparkCounterValidationRule("resultSerializationTime", Some(1000), None))
    )
    val v = Validation(sc, vc)
    val acc = sc.accumulator(0)
    // We run this simple job 2x, but since we expect a failure it shouldn't skew the average
    runSimpleJob(sc, acc)
    runSimpleJob(sc, acc)
    assert(v.validate(2) === false)
  }

  test("basic historic rule") {
    val vc = new ValidationConf(tempPath, "1", true,
      List[ValidationRule](new AvgRule("acc", 0.001, Some(200))))
    val v = Validation(sc, vc)
    val acc = sc.accumulator(0)
    v.registerAccumulator(acc, "acc")
    runSimpleJob(sc, acc)
    assert(v.validate(3) === true)
  }
}
