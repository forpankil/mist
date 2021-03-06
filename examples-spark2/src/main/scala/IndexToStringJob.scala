import io.hydrosphere.mist.lib.spark2._
import io.hydrosphere.mist.lib.spark2.ml._
import org.apache.spark.ml.Pipeline
import org.apache.spark.ml.feature.{IndexToString, StringIndexer}

object IndexToStringJob extends MLMistJob with SQLSupport {

  def train(savePath: String): Map[String, Any] = {
    val df = session.createDataFrame(Seq(
      (0, "a"),
      (1, "b"),
      (2, "c"),
      (3, "a"),
      (4, "a"),
      (5, "c")
    )).toDF("id", "category")

    val indexer = new StringIndexer()
      .setInputCol("category")
      .setOutputCol("categoryIndex")
      .fit(df)

    val converter = new IndexToString()
      .setInputCol("categoryIndex")
      .setOutputCol("originalCategory")

    val pipeline = new Pipeline().setStages(Array(indexer, converter))

    val model = pipeline.fit(df)

    model.write.overwrite().save("models/index")
    Map.empty[String, Any]
  }

  def serve(modelPath: String, features: List[Double]): Map[String, Any] = {
    import LocalPipelineModel._

    val features = List(
      "a", "b", "c", "c"
    )

    val pipeline = PipelineLoader.load(modelPath)
    val data = LocalData(
      LocalDataColumn("category", features)
    )

    val result: LocalData = pipeline.transform(data)
    Map("result" -> result.select("category", "categoryIndex").toMapList)
  }
}
