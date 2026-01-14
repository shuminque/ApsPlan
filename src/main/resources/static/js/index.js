// $(function () {
//     // echart_1();
//     echart_2();
//     echart_3();
//
//     function echart_1() {
//         // 基于准备好的dom，初始化echarts实例
//         var myChart = echarts.init(document.getElementById('chart_1'));
//         var xAxisData = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11', '12','13','14','15','16','17','18','19','20'];
//         var legendData = ['3月', '4月'];
//         var title = "2018年度3月,4月销售额";//标题
//         var serieData = [];
//         var metaDate = [
//             [1200, 1400, 1000, 1200, 3000, 2300, 1300, 1700, 1400, 1200, 3000, 2300, 2400, 2100, 2800, 3100, 3000, 2800, 2700, 2900],
//             [2000, 1200, 3000, 2000, 1700, 3000, 2000, 1800, 2000, 1900, 3000, 2000, 2500, 2200, 2600, 2700, 2900, 3000, 3200, 3100]
//         ]
//         for (var v = 0; v < legendData.length; v++) {
//             var serie = {
//                 name: legendData[v],
//                 type: 'line',
//                 symbol: "circle",
//                 symbolSize: 10,
//                 data: metaDate[v]
//             };
//             serieData.push(serie);
//         }
//         var colors = ["#036BC8", "#FFF", "#5EBEFC", "#2EF7F3"];
//         var option = {
//             // backgroundColor: '#0f375f',
//             title: {
//                 text: title,
//                 textAlign: 'left',
//                 textStyle: {
//                     color: "#fff",
//                     fontSize: "12",
//                     fontWeight: "bold"
//                 }
//             },
//             legend: {
//                 show: true,
//                 left: "center",
//                 data: legendData,
//                 y: "5%",
//                 itemWidth: 18,
//                 itemHeight: 12,
//                 textStyle: {
//                     color: "#fff",
//                     fontSize: 14
//                 },
//             },
//             toolbox: {
//                 orient: 'vertical',
//                 right: '1%',
//                 top: '20%',
//                 iconStyle: {
//                     color: '#fff',
//                     borderColor: '#fff',
//                     borderWidth: 1,
//                 },
//                 feature: {
//                     saveAsImage: {},
//                     magicType: {
//                         // show: true,
//                         type: ['line','bar','stack','tiled']
//                     }
//                 }
//             },
//             color: colors,
//             grid: {
//                 left: '2%',
//                 top: "12%",
//                 bottom: "5%",
//                 right: "5%",
//                 containLabel: true
//             },
//             tooltip: {
//                 trigger: 'axis',
//                 axisPointer: {
//                     type: 'shadow'
//                 },
//             },
//             xAxis: [{
//                 type: 'category',
//                 axisLine: {
//                     show: true,
//                     lineStyle: {
//                         color: '#6173A3'
//                     }
//                 },
//                 axisLabel: {
//                     interval: 0,
//                     textStyle: {
//                         color: '#9ea7c4',
//                         fontSize: 12
//                     }
//                 },
//                 axisTick: {
//                     show: false
//                 },
//                 data: xAxisData,
//             }, ],
//             yAxis: [{
//                 axisTick: {
//                     show: false
//                 },
//                 splitLine: {
//                     show: false
//                 },
//                 axisLabel: {
//                     textStyle: {
//                         color: '#9ea7c4',
//                         fontSize: 12
//                     }
//                 },
//                 axisLine: {
//                     show: true,
//                     lineStyle: {
//                         color: '#6173A3'
//                     }
//                 },
//             }, ],
//             series: serieData
//         };
//         // 使用刚指定的配置项和数据显示图表。
//         myChart.setOption(option);
//         window.addEventListener("resize", function () {
//             myChart.resize();
//         });
//     }
//
//     function echart_2() {
//         // 基于准备好的dom，初始化echarts实例
//         var myChart = echarts.init(document.getElementById('chart_2'));
//         myChart.setOption({
//             graphic: {
//                 elements: [{
//                     type: 'image',
//                     style: {
//                         width: 30,
//                         height: 30
//                     },
//                     left: '73%',
//                     top: 'center'
//                 }]
//             },
//             tooltip: {
//                 trigger: 'axis',
//                 axisPointer: {
//                     type: 'shadow'
//                 }
//             },
//             legend: {
//                 data: ['销售额'],
//                 left:'27%'
//             },
//             grid: {
//                 left: '1%',
//                 right: '60%',
//                 top: '10%',
//                 bottom: '10%',
//                 containLabel: true,
//             },
//             xAxis: {
//                 type: 'value',
//                 position:'top',
//                 splitLine: {show: false},
//                 boundaryGap: [0, 0.01],
//                 axisTick: {
//                     show: false
//                 },
//                 axisLabel: {
//                     textStyle: {
//                         color: '#9ea7c4',
//                         fontSize: 12
//                     }
//                 },
//                 axisLine: {
//                     show: true,
//                     lineStyle: {
//                         color: '#6173A3'
//                     }
//                 },
//             },
//             yAxis: {
//                 type: 'category',
//                 data: ['A','B','C','D','E'],
//                 axisTick: {
//                     show: false
//                 },
//                 splitLine: {
//                     show: false
//                 },
//                 axisLabel: {
//                     textStyle: {
//                         color: '#9ea7c4',
//                         fontSize: 12
//                     }
//                 },
//                 axisLine: {
//                     show: true,
//                     lineStyle: {
//                         color: '#6173A3'
//                     }
//                 },
//             },
//             series: [{
//                 name: '',
//                 itemStyle: {
//                     normal: {
//                         color: function(params) {
//                             // build a color map as your need.
//                             var colorList = [
//                                 '#C1232B','#B5C334','#FCCE10','#E87C25','#27727B',
//                                 '#FE8463','#9BCA63','#FAD860','#F3A43B','#60C0DD',
//                                 '#D7504B','#C6E579','#F4E001','#F0805A','#26C0C0'
//                             ];
//                             return colorList[params.dataIndex]
//                         },
//                         shadowBlur: 20,
//                         shadowColor: 'rgba(0, 0, 0, 0.5)'
//                     }
//                 },
//                 type: 'bar',
//                 data: [260,210,190,170,170]
//             },{
//                 type: 'pie',
//                 radius: [30, '80%'],
//                 center: ['75%', '50%'],
//                 roseType: 'radius',
//                 color: [ '#C1232B','#B5C334','#FCCE10','#E87C25','#27727B',
//                 '#FE8463','#9BCA63','#FAD860','#F3A43B','#60C0DD',
//                 '#D7504B','#C6E579','#F4E001','#F0805A','#26C0C0'],
//                 data: [{
//                     value: 26,
//                     name: 'A'
//                 }, {
//                     value: 21,
//                     name: 'B'
//                 }, {
//                     value: 19,
//                     name: 'C'
//                 }, {
//                     value: 17,
//                     name: 'D'
//                 }, {
//                     value: 17,
//                     name: 'E'
//                 }],
//                 label: {
//                     normal: {
//                         textStyle: {
//                             fontSize: 14
//                         },
//                         formatter: function(param) {
//                             return param.name + ':\n' + Math.round(param.percent) + '%';
//                         }
//                     }
//                 },
//                 labelLine: {
//                     normal: {
//                         smooth: true,
//                         lineStyle: {
//                             width: 2
//                         }
//                     }
//                 },
//                 itemStyle: {
//                     normal: {
//                         shadowBlur: 30,
//                         shadowColor: 'rgba(0, 0, 0, 0.4)'
//                     }
//                 },
//
//                 animationType: 'scale',
//                 animationEasing: 'elasticOut',
//                 animationDelay: function(idx) {
//                     return Math.random() * 200;
//                 }
//             }]
//         });
//     }
//
//     function echart_3() {
//         // 基于准备好的dom，初始化echarts实例
//         var myChart = echarts.init(document.getElementById('chart_3'));
//
//         option = {
//             // backgroundColor: "#404A59",
//             color: ["#036BC8", "#5EBEFC", "#2EF7F3"],
//
//             title: [{
//                 text: '',
//                 left: '1%',
//                 top: '6%',
//                 textStyle: {
//                     color: '#fff'
//                 }
//             }, {
//                 text: '',
//                 left: '83%',
//                 top: '6%',
//                 textAlign: 'center',
//                 textStyle: {
//                     color: '#fff',
//                     fontSize: 16
//                 }
//             }],
//             tooltip: {
//                 trigger: 'axis'
//             },
//             legend: {
//                 x: 300,
//                 top: '7%',
//                 textStyle: {
//                     color: '#ffd285',
//                 },
//                 data: ['2016年', '2017年', '2018年']
//             },
//             grid: {
//                 left: '1%',
//                 right: '28%',
//                 top: '16%',
//                 bottom: '6%',
//                 containLabel: true
//             },
//             toolbox: {
//                 "show": false,
//                 feature: {
//                     saveAsImage: {}
//                 }
//             },
//             xAxis: {
//                 type: 'category',
//                 "axisLine": {
//                     lineStyle: {
//                         color: '#fff'
//                     }
//                 },
//                 "axisTick": {
//                     "show": false
//                 },
//                 axisLabel: {
//                     textStyle: {
//                         color: '#fff'
//                     }
//                 },
//                 boundaryGap: false,
//                 data: ['1', '2', '3', '4', '5', '6', '7','8','9','10','11','12']
//             },
//             yAxis: {
//                 "axisLine": {
//                     lineStyle: {
//                         color: '#fff'
//                     }
//                 },
//                 splitLine: {
//                     show: false,
//                     lineStyle: {
//                         color: '#fff'
//                     }
//                 },
//                 "axisTick": {
//                     "show": false
//                 },
//                 axisLabel: {
//                     textStyle: {
//                         color: '#fff'
//                     }
//                 },
//                 type: 'value'
//             },
//             series: [{
//                 name: '',
//                 smooth: true,
//                 type: 'line',
//                 symbolSize: 9,
//                   symbol: 'circle',
//                 data: [90, 50, 39, 50, 120, 82, 80, 89, 92, 80, 102, 77]
//             }, {
//                 name: '',
//                 smooth: true,
//                 type: 'line',
//                 symbolSize: 9,
//                   symbol: 'circle',
//                 data: [70, 50, 50, 87, 90, 80, 70, 77, 86, 94, 96, 99]
//             }, {
//                 name: '',
//                 smooth: true,
//                 type: 'line',
//                 symbolSize: 9,
//                   symbol: 'circle',
//                 data: [100, 112, 80, 132, 60, 70, 90, 131, 121, 102, 95, 105 ]
//             },
//             {
//                 type: 'pie',
//                 center: ['83%', '33%'],
//                 radius: ['30%', '35%'],
//                 label: {
//                     normal: {
//                         position: 'center'
//                     }
//                 },
//                 data: [{
//                     value: 335,
//                     name: '销售分析',
//                     itemStyle: {
//                         normal: {
//                             color: '#FF7E45'
//                         }
//                     },
//                     label: {
//                         normal: {
//                             formatter: '{d} %',
//                             textStyle: {
//                                 color: '#ffd285',
//                                 fontSize: 14
//
//                             }
//                         }
//                     }
//                 }, {
//                     value: 180,
//                     name: '占位',
//                     tooltip: {
//                         show: false
//                     },
//                     itemStyle: {
//                         normal: {
//                             color: '#fff'
//                         }
//                     },
//                     label: {
//                         normal: {
//                             textStyle: {
//                                 color: '#ffd285',
//                             },
//                             formatter: '\n销售渠道'
//                         }
//                     }
//                 }]
//             },
//
//
//             {
//                 type: 'pie',
//                 center: ['83%', '72%'],
//                 radius: ['30%', '35%'],
//                 label: {
//                     normal: {
//                         position: 'center'
//                     }
//                 },
//                 data: [{
//                     value: 435,
//                     name: '销售分析',
//                     itemStyle: {
//                         normal: {
//                             color: '#4834CB'
//                         }
//                     },
//                     label: {
//                         normal: {
//                             formatter: '{d} %',
//                             textStyle: {
//                                 color: '#fff',
//                                 fontSize: 14
//
//                             }
//                         }
//                     }
//                 }, {
//                     value: 100,
//                     name: '占位',
//                     tooltip: {
//                         show: false
//                     },
//                     itemStyle: {
//                         normal: {
//                             color: '#fff'
//
//
//                         }
//                     },
//                     label: {
//                         normal: {
//                             textStyle: {
//                                 color: '#fff',
//                             },
//                             formatter: '\n销售方向'
//                         }
//                     }
//                 }]
//             }]
//         }
//
//         // 使用刚指定的配置项和数据显示图表。
//         myChart.setOption(option);
//         window.addEventListener("resize", function () {
//             myChart.resize();
//         });
//     }
// });