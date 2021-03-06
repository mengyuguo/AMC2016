package com.alibaba.middleware.race.jstorm;

import java.util.HashMap;
import java.util.Map;

import com.alibaba.middleware.race.model.PaymentMessage;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.IRichBolt;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;

@SuppressWarnings("serial")
public class TaobaoStatistic implements IRichBolt {
	OutputCollector collector;
	Map<Long, Double> res = new HashMap<Long, Double>();
	long prePayTime = 0;
	boolean first = true;
//	int seq = 0;

	@Override
	public void execute(Tuple tuple) {
		if (tuple.getValue(0).equals("0x00")) {//收到流结束的标志
			collector.emit("PcOrWireless_Stream_Id", new Values("0x00")); //也对下一级的Bolt发送借宿标志
			if(prePayTime == 0) {//第一次就直接收到流结束标志
				return; 
			} else { //把最后一分钟内的消息推送出去
				
				if (res.containsKey(prePayTime)) {
//					System.out.println("taobao结束  "+prePayTime+": "+res.get(prePayTime));
					collector.emit(new Values(prePayTime, res.get(prePayTime)));
					res.remove(prePayTime);
				}
			}			
		} else {
			PaymentMessage payment = (PaymentMessage) tuple.getValue(0);
			collector.emit("PcOrWireless_Stream_Id", new Values(payment)); //对未处理的消息进行下发
			long createTime = (payment.getCreateTime() / RaceTopology.Interval) * 60;
			Double amount = res.get(createTime);
			res.put(createTime, amount==null ? payment.getPayAmount() : amount+payment.getPayAmount());
			if(first) {
				first = false;
				prePayTime = createTime;
			}else if(createTime != prePayTime ) { // 已经到了下一个一分钟,把数据传出，同时删掉map中对数据的存储
				if (res.containsKey(prePayTime)) {
//					seq++;
//					System.out.println("taobao "+seq+": "+prePayTime+": "+res.get(prePayTime));
					collector.emit(new Values(prePayTime, res.get(prePayTime)));
					res.remove(prePayTime);
				}
				prePayTime = createTime;
			}
		}
	}

	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		declarer.declare(new Fields("minute", "amount"));
		declarer.declareStream("PcOrWireless_Stream_Id", new Fields("message"));
	}

	@SuppressWarnings("rawtypes")
	@Override
	public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
		this.collector = collector;
	}

	@Override
	public void cleanup() {
		// TODO Auto-generated method stub

	}

	@Override
	public Map<String, Object> getComponentConfiguration() {
		// TODO Auto-generated method stub
		return null;
	}
}
