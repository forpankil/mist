package io.hydrosphere.mist.lib.spark2.ml.preprocessors

import io.hydrosphere.mist.lib.spark2.ml._
import org.apache.spark.ml.feature.StandardScalerModel
import org.apache.spark.ml.linalg.{DenseVector, Vector}
import org.apache.spark.mllib.feature.{StandardScalerModel => OldStandardScalerModel}
import org.apache.spark.mllib.linalg.{Vector => OldVector, Vectors => OldVectors}

class LocalStandardScalerModel(override val sparkTransformer: StandardScalerModel) extends LocalTransformer[StandardScalerModel] {
  override def transform(localData: LocalData): LocalData = {
    localData.column(sparkTransformer.getInputCol) match {
      case Some(column) =>
        val scaler = new OldStandardScalerModel(
          OldVectors.fromML(sparkTransformer.std.asInstanceOf[Vector]),
          OldVectors.fromML(sparkTransformer.mean.asInstanceOf[Vector]),
          sparkTransformer.getWithStd,
          sparkTransformer.getWithMean
        )

        val newData = column.data.map(r => {
          val vec: List[Double] = r match {
            case d: List[Any @unchecked] =>
              val l: List[Double] = d map (_.toString.toDouble)
              l
            case d => throw new IllegalArgumentException(s"Unknown data type for LocalStandardScaler: $d")
          }
          val vector: OldVector = OldVectors.dense(vec.toArray)
          scaler.transform(vector)
        })
        localData.withColumn(LocalDataColumn(sparkTransformer.getOutputCol, newData))
      case None => localData
    }
  }
}

object LocalStandardScalerModel extends LocalModel[StandardScalerModel] {
  override def load(metadata: Metadata, data: Map[String, Any]): StandardScalerModel = {
    val constructor = classOf[StandardScalerModel].getDeclaredConstructor(classOf[String], classOf[Vector], classOf[Vector])
    constructor.setAccessible(true)

    val stdVals = data("std").asInstanceOf[Map[String, Any]].getOrElse("values", List()).asInstanceOf[List[Double]].toArray
    val std = new DenseVector(stdVals)

    val meanVals = data("mean").asInstanceOf[Map[String, Any]].getOrElse("values", List()).asInstanceOf[List[Double]].toArray
    val mean = new DenseVector(meanVals)
    constructor
      .newInstance(metadata.uid, std, mean)
      .setInputCol(metadata.paramMap("inputCol").asInstanceOf[String])
      .setOutputCol(metadata.paramMap("outputCol").asInstanceOf[String])
  }

  override implicit def getTransformer(transformer: StandardScalerModel): LocalTransformer[StandardScalerModel] = new LocalStandardScalerModel(transformer)
}
