class PMTest extends TestBase {

  def runEdgeGensingle(): Unit = {
    performanceModeling("./data/" + filename, tile_xy)
  }

  def runEdgeGen(): Unit = {
    var filenameStart       = 1
    var filenameEnd       = 10
    var totalEfficiency   = 0.0

    for (itr <- filenameStart to filenameEnd) {
      filename = s"Gset/G$itr"
      val efficiency = performanceModeling("./data/" + filename, tile_xy)
      totalEfficiency += efficiency
    }
    val averageEfficiency = totalEfficiency / (filenameEnd - filenameStart + 1)
    println(averageEfficiency)
  }
}


object PMTest extends App {
  val pm_test = new PMTest
//  pm_test.runEdgeGensingle()
  pm_test.runEdgeGen()
}