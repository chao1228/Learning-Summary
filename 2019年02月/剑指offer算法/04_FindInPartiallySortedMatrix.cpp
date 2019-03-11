#include <iostream>
using namespace std;

//c++用通常用一维数组表示二维数组，c++没有直接返回数组长度的函数

bool Find(int* matrix,int rows,int columns,int number)
{
	bool found = false;
	if(rows >0 && columns > 0)
	{
		int row = 0;
		int column = columns - 1; 
		while(row>=0 && column >=0 && row < rows && column < columns)
		{
			if(matrix[row*columns + column] == number)
			{
				found = true;
				cout<<row+1<<"行"<<column+1<<"列"<<endl;
				break;
			}else if(matrix[row*columns + column] < number)
				row++;
			else
				column--;
		}
	}
	cout<<"没有找到"<<endl;
	return found;
}

int main()
{
	int data[][4]={{1,2,8,9},{2,4,9,12},{4,7,10,13},{6,8,11,15}};
	Find((int*)data,4,4,7);
	Find((int*)data,4,4,5);
	Find((int*)data,4,4,1);
	Find((int*)data,4,4,15);
	Find((int*)data,4,4,0);
	Find((int*)data,4,4,16);
}



