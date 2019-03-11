/*g++ -o 数组中重复的数字 数组中 重复的数字.cpp*/


#include <iostream>
using namespace std;


int countRange(const int numbers[], int length, int start,int end)
{
	if(numbers == NULL)
		return -1;
	int count = 0;
	for(int i = 0;i<length;i++)
	{
		if(numbers[i] <= end && numbers[i]>=start)
			count ++;
	}
	return count;
}

int duplication(const int numbers[], int length)
{
	
	if(numbers == NULL || length <= 0)
	{
		return -1;
	}
	for(int i=0;i<length;i++)
	{
		if(numbers[i]>length-1)
		{	std::cout<<"数字不合法"<<std::endl;
			return -1;
		}
	}
	int start = 1;
	int end = length-1;
	while(end >= start)
	{
		int middle = ((end-start)>>1)+start;
		int count = countRange(numbers,length,start,middle);
		if(end == start)
		{
			if(count > 1)
				return start;
			else
				std::cout<<"没有重复数字"<<std::endl;
				return -1;
		}
		if(count > (middle-start+1))
			end = middle;
		else
			start = middle+1;
		
	}
	std::cout<<"没有重复数字"<<std::endl;
	return -1;
}






int main()
{
	int data[]={2,3,5,4,7,2,6,7};
	int duplicationNumber=duplication(data,8);
	std::cout<<"方法1："<<std::endl;
	std::cout<<duplicationNumber<<std::endl;
}

