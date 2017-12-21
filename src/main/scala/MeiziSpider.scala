import com.scrapy4s.extractor.Extractor
import com.scrapy4s.http.{Request, RequestWithData, ResponseWithData}
import com.scrapy4s.manage.CmdManage
import com.scrapy4s.monitor.CountLogMonitor
import com.scrapy4s.pipeline.FileDumpPipeline
import com.scrapy4s.spider.Spider
import com.scrapy4s.util.HashUtil
import com.scrapy4s.util.UrlUtil._

/**
  * Created by sheep3 on 2017/12/21.
  */
object MeiziSpider {
  def main(args: Array[String]): Unit = {

    /**
      * 下载器
      */
    val downloader = Spider("downloader")
      .setMonitor(CountLogMonitor())
      .setTimeOut(10 * 1000)
      .pipe(
        /**
          * 修改为你的文件夹路径
          */
        FileDumpPipeline("/Users/admin/data/img/") {
          case r: ResponseWithData[String] =>
            r.message
        }
      )

    /**
      * 详情页
      */
    val detail = Spider("detail")
      .setMonitor(CountLogMonitor())
      .pipe(r => {
        val title = Extractor.xpath("""//div[@class="metaRight"]/h2/a/text()""", r.body("gb2312"))
          .headOption.getOrElse(HashUtil.getHash(r.url))
          .replaceAll("""/""", "")
          .replaceAll("""\s""", "")

        r.regex("""<img alt=".*?src="(.*?)" /><br />""")
          .map(_ (0))
          .zipWithIndex
          .foreach {
            case (url, i) => downloader.execute(RequestWithData(url, s"${title}_$i.jpg"))
          }
      })

    /**
      * 列表页
      */
    val list = Spider("list")
      .setMonitor(CountLogMonitor())
      .pipeForRequest(implicit r => {
        /**
          * 获取详情页
          */
        r.regex("""<a target='_blank' href="(.*?)">""")
          .map(_ (0).url)
          .foreach(url => detail.execute(Request(url)))

        /**
          * 获取新的列表页
          */
        r.regex("""<li><a href='(.*?)'>""")
          .map(_ (0))
          .map(i => Request(s"http://www.meizitu.com/a/$i"))
      })
      .setStartUrl(Request(s"http://www.meizitu.com/a/more_1.html"))


    CmdManage()
      .setHistory(true)
      .register(downloader)
      .register(detail)
      .register(list)
      .start()

  }
}
