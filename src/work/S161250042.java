package work;

import bottom.BottomMonitor;
import bottom.BottomService;
import bottom.Task;
import main.Schedule;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

/**
 * 注意：请将此类名改为 S+你的学号   eg: S161250001
 * 提交时只用提交此类和说明文档
 * <p>
 * 在实现过程中不得声明新的存储空间（不得使用new关键字，反射和java集合类）
 * 所有新声明的类变量必须为final类型
 * 不得创建新的辅助类
 * <p>
 * 可以生成局部变量
 * 可以实现新的私有函数
 * <p>
 * 可用接口说明:
 * <p>
 * 获得当前的时间片
 * int getTimeTick()
 * <p>
 * 获得cpu数目
 * int getCpuNumber()
 * <p>
 * 对自由内存的读操作  offset 为索引偏移量， 返回位置为offset中存储的byte值
 * byte readFreeMemory(int offset)
 * <p>
 * 对自由内存的写操作  offset 为索引偏移量， 将x写入位置为offset的内存中
 * void writeFreeMemory(int offset, byte x)
 */
public class S161250042 extends Schedule {
	/**
	 * int占用字节数
	 */
	private final static int INT_SIZE = 4;
	/*
	 * PCB节点:
	 * offset   type    name
	 * 0        int     tid
	 * 4        int     leftTime
	 * 8        int     lastTaskIndex
	 * 12       int     nextTaskIndex
	 * 16       int     resource.length
	 * 20...    int...  resourceIds
	 */
	private final static int PCB_OFFSET_ID = 0;
	private final static int PCB_OFFSET_LEFT_TIME = 4;
	private final static int PCB_OFFSET_LAST_INDEX = 8;
	private final static int PCB_OFFSET_NEXT_INDEX = 12;
	private final static int PCB_OFFSET_RESOURCE_LENGTH = 16;
	private final static int PCB_OFFSET_RESOURCE_ID = 20;
	/**
	 * 资源位示图
	 */
	private final static int RESOURCE_BITMAP = 0;
	/**
	 * 等待队列中第一个PCB的存储空间的起始地址
	 */
	private final static int FIRST_PCB_START_ADDRESS = RESOURCE_BITMAP + 128;
	/**
	 * 下一个待添加PCB的存储空间的起始地址
	 */
	private final static int NEW_PCB_START_ADDRESS = FIRST_PCB_START_ADDRESS + INT_SIZE;

	/**
	 * 最短作业优先算法（抢占式）
	 * 进程调度代码
	 *
	 * @param arrivedTask 到达任务数组， 数组长度不定
	 * @param cpuOperate  （返回值）cpu操作数组  数组长度为cpuNumber
	 *                    cpuOperate[0] = 1 代表cpu0在当前时间片要执行任务1
	 *                    cpuOperate[1] = 2 代表cpu1在当前时间片要执行任务2
	 *                    cpuOperate[2] = 0 代表cpu1在当前时间片空闲什么也不做
	 */
	@Override
	public void ProcessSchedule(Task[] arrivedTask, int[] cpuOperate) {
		// 将任务按所需时间升序排序
		Arrays.sort(arrivedTask, Comparator.comparingInt(o -> o.cpuTime));
		// 记录新到任务
		int index = this.readInt(FIRST_PCB_START_ADDRESS);
		int before = 0;
		nextTask:
		for (Task task : arrivedTask) {
			// 扫描链表
			while (index != 0) {
				int leftTime = this.readInt(index + PCB_OFFSET_LEFT_TIME);
				if (task.cpuTime >= leftTime) {
					// 扫描下一节点
					before = index;
					index = this.readInt(index + PCB_OFFSET_NEXT_INDEX);
				} else {
					// 在index节点前面插入PCB
					int newIndex = this.getNewPcbStartAddress();
					this.addTask(task, newIndex, before, index);
					this.writeInt(index + PCB_OFFSET_LAST_INDEX, newIndex);
					if (before != 0) {
						this.writeInt(before + PCB_OFFSET_NEXT_INDEX, newIndex);
					}
					before = newIndex;
					continue nextTask;
				}
			}
			// 已扫描到结尾，在结尾添加节点
			int newIndex = this.getNewPcbStartAddress();
			this.addTask(task, newIndex, before, 0);
			if (before != 0) {
				this.writeInt(before + PCB_OFFSET_NEXT_INDEX, newIndex);
			}
			before = newIndex;
		}

//		if (arrivedTask.length > 0) {
//			this.printTaskQueue();
//		}

		// 扫描链表制定CPU策略
		// 释放所有资源
		for (int i = 0; i < 128; i++) {
			this.writeFreeMemory(RESOURCE_BITMAP + i, (byte) 0);
		}
		// 扫描链表寻找可以做的任务
		index = this.readInt(FIRST_PCB_START_ADDRESS);
		for (int i = 0; i < this.getCpuNumber() && index > 0; index = this.readInt(index + PCB_OFFSET_NEXT_INDEX)) {
			// 检查所需资源是否都准备就绪
			boolean isAllResourcesReady = true;
			int resourceLength = this.readInt(index + PCB_OFFSET_RESOURCE_LENGTH);
			for (int j = resourceLength - 1; j >= 0; j--) {
				int resourceId = this.readInt(index + PCB_OFFSET_RESOURCE_ID + j * INT_SIZE);
				if (this.readFreeMemory(RESOURCE_BITMAP + resourceId - 1) == 1) {
					isAllResourcesReady = false;
					break;
				}
			}
			if (isAllResourcesReady) {
				// 将资源交给该任务独占
				for (int j = resourceLength - 1; j >= 0; j--) {
					int resourceId = this.readInt(index + PCB_OFFSET_RESOURCE_ID + j * INT_SIZE);
					this.writeFreeMemory(RESOURCE_BITMAP + resourceId - 1, (byte) 1);
				}
				// 将任务交给CPU运行
				cpuOperate[i] = this.readInt(index + PCB_OFFSET_ID);
				this.doTask(index);
				i++;
			}
		}
	}

	private void printTaskQueue() {
		int index = this.readInt(FIRST_PCB_START_ADDRESS);
		while (index != 0) {
			System.out.print(index
					+ " ID: " + this.readInt(index + PCB_OFFSET_ID)
					+ ", left time: " + this.readInt(index + PCB_OFFSET_LEFT_TIME) + ", "
					+ this.readInt(index + PCB_OFFSET_RESOURCE_LENGTH) + " resources: ");
//			for (int j = this.readInt(index + PCB_OFFSET_RESOURCE_LENGTH); j > 0; j--) {
//				int resourceId = this.readInt(index + PCB_OFFSET_RESOURCE_ID + j * INT_SIZE);
//				this.writeFreeMemory(RESOURCE_BITMAP + resourceId - 1, (byte) 1);
//				System.out.print(resourceId + "(" + (index + PCB_OFFSET_RESOURCE_ID + j * INT_SIZE) + ")" + " ");
//			}
			System.out.println();
			index = this.readInt(index + PCB_OFFSET_NEXT_INDEX);
		}
		System.out.println();
	}

	/**
	 * 剩余所需时间--，若结果为零则删除该节点
	 */
	private void doTask(int index) {
		int leftTime = this.readInt(index + PCB_OFFSET_LEFT_TIME) - 1;
		if (leftTime > 0) {
			this.writeInt(index + PCB_OFFSET_LEFT_TIME, leftTime);
		} else {
			// 删除节点
			int last = this.readInt(index + PCB_OFFSET_LAST_INDEX);
			int next = this.readInt(index + PCB_OFFSET_NEXT_INDEX);
			if (last != 0) {
				this.writeInt(last + PCB_OFFSET_NEXT_INDEX, next);
			}
			if (next != 0) {
				this.writeInt(next + PCB_OFFSET_LAST_INDEX, last);
			}
			if (this.readInt(FIRST_PCB_START_ADDRESS) == index) {
				this.writeInt(FIRST_PCB_START_ADDRESS, next);
			}

//			System.out.println("Task " + this.readInt(index + PCB_OFFSET_ID) + " finished at time " + this.getTimeTick());
		}
	}

	private int getNewPcbStartAddress() {
		int ret = this.readInt(NEW_PCB_START_ADDRESS);
		if (ret == 0) { // 初始化
			ret = NEW_PCB_START_ADDRESS + INT_SIZE;
			this.writeInt(NEW_PCB_START_ADDRESS, ret);
			this.writeInt(FIRST_PCB_START_ADDRESS, ret);
		}
		return ret;
	}

	/**
	 * 双向链表实现等待队列，维持其以剩余所需时间的升序排序
	 */
	private void addTask(Task toWrite, int index, int lastTaskIndex, int nextTaskIndex) {
		// 写入PCB
		this.writeInt(index + PCB_OFFSET_ID, toWrite.tid);
		this.writeInt(index + PCB_OFFSET_LEFT_TIME, toWrite.cpuTime);
		this.writeInt(index + PCB_OFFSET_LAST_INDEX, lastTaskIndex);
		this.writeInt(index + PCB_OFFSET_NEXT_INDEX, nextTaskIndex);
		this.writeInt(index + PCB_OFFSET_RESOURCE_LENGTH, toWrite.resource.length);
		for (int i = 0; i < toWrite.resource.length; i++) {
			this.writeInt(index + PCB_OFFSET_RESOURCE_ID + i * INT_SIZE, toWrite.resource[i]);
		}
		// 更新下一个待添加PCB的起始地址
		this.writeInt(NEW_PCB_START_ADDRESS, index + PCB_OFFSET_RESOURCE_ID + toWrite.resource.length * INT_SIZE);
		// 检查等待队列是否已空，若空则标记新添加的任务为头节点
		if (this.readInt(FIRST_PCB_START_ADDRESS) == 0) {
			this.writeInt(FIRST_PCB_START_ADDRESS, index);
		}
	}

	private void writeInt(int offset, int n) {
		this.writeFreeMemory(offset, (byte) (n >> 24));
		this.writeFreeMemory(offset + 1, (byte) (n >> 16));
		this.writeFreeMemory(offset + 2, (byte) (n >> 8));
		this.writeFreeMemory(offset + 3, (byte) n);
	}

	private int readInt(int offset) {
		int ret = ((this.readFreeMemory(offset) & 0xff) << 24);
		ret |= ((this.readFreeMemory(offset + 1) & 0xff) << 16);
		ret |= ((this.readFreeMemory(offset + 2) & 0xff) << 8);
		ret |= (this.readFreeMemory(offset + 3) & 0xff);
		return ret;
	}


//	public static void main(String[] args) throws IOException {
//		// 定义cpu的数量
//		int cpuNumber = 2;
//		// 定义测试文件
//		String filename = "src/testFile/textSample.txt";
//
//		BottomMonitor bottomMonitor = new BottomMonitor(filename, cpuNumber);
//		BottomService bottomService = new BottomService(bottomMonitor);
//		S161250042 s161250042 = new S161250042();
//		s161250042.setBottomService(bottomService);
//
//		s161250042.ProcessSchedule(new Task[0], new int[0]);
////		int offset = 0;
////		s161250042.writeInt(offset, 0xffffff);
////		System.out.println(s161250042.readInt(offset));
//		int tid = 1;
//		int[] resources = {1, 2, 128};
//		Task task = new Task(tid, 666, resources);
//		s161250042.addTask(task, 0, 0, 0);
//		System.out.print("task info");
//	}

	private static void test10Rounds(int cpuNumber) throws Exception {
		// 定义测试文件
		for(int n=1; n<=10; n++){
			String filename = "src/testFile/rand_" + n + ".csv";
			BottomMonitor bottomMonitor = new BottomMonitor(filename, cpuNumber);
			BottomService bottomService = new BottomService(bottomMonitor);
			Schedule schedule = new S161250042();
			schedule.setBottomService(bottomService);
			//外部调用实现类
			for (int i = 0; i < 1000; i++) {
				Task[] tasks = bottomMonitor.getTaskArrived();
				int[] cpuOperate = new int[cpuNumber];
				schedule.ProcessSchedule(tasks, cpuOperate);
				bottomService.runCpu(cpuOperate);
				bottomMonitor.increment();
			}
			//打印统计结果
			bottomMonitor.printStatistics();
			System.out.println();
//			//打印任务队列
//			bottomMonitor.printTaskArrayLog();
//			System.out.println();
//			//打印cpu日志
//			bottomMonitor.printCpuLog();
		}
	}

	/**
	 * 执行主函数 用于debug
	 * 里面的内容可随意修改
	 * 你可以在这里进行对自己的策略进行测试，如果不喜欢这种测试方式，可以直接删除main函数
	 */
	public static void main(String[] args) throws IOException {
		// 定义cpu的数量
		int cpuNumber = 2;
		try {
			test10Rounds(cpuNumber);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
