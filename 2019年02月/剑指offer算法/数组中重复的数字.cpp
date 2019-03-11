/*g++ -o 数组中重复的数字 数组中 重复的数字.cpp*/


#include <iostream>
using namespace std;

bool duplication(int numbers[], int length, int* duplication)
{
	if(numbers == NULL || length <= 0)
	{
		return false;
	}
	for(int i=0; i<length; i++)
	{
		if(numbers[i] < 0 || numbers[i] > length-1 )
			return false;
	}
	
	for(int i=0; i<length; i++)
	{
		while(numbers[i] != i)
		{
			if(numbers[i] == numbers[numbers[i]]){
				*duplication = numbers[i];
				return true;
			}
			int temp = numbers[numbers[i]];
			numbers[numbers[i]] = numbers[i];
			numbers[i] = temp;
		}
	}
	return false;
}

bool duplication2(int numbers[], int length, int* duplication)
{
	if(numbers == NULL || length < 0)
	{
		return false;
	}
	for(int i=0; i<length;i++)
	{
		if(numbers[i] > length-1)
			return false;
	}
	int map[255] = {0};
	for(int i=0; i<length;i++)
	{
		map[numbers[i]]++;
	}
	for(int i=0; i<length;i++)
	{
		if(map[i]>1)
		{
			*duplication = i;
			return true;
		}
	}
	return false;
	
}




int main()
{
	int data[]={2,3,1,0,3,5,3};
	int duplicationNumber = 0;
	duplication(data,7,&duplicationNumber);
	std::cout<<"方法1："<<std::endl;
	std::cout<<duplicationNumber<<std::endl;

	
	
	
	duplication2(data,7,&duplicationNumber);
	std::cout<<"方法2："<<std::endl;
	std::cout<<duplicationNumber<<std::endl;

}

