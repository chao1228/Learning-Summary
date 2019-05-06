/*重点: 1、函数使用 substr\find_last_not_of
		2、转义字符"\\"
		3、计数加题，errorRecords[i]->count = errorRecords[i]->count+1;
	

	
开发一个简单错误记录功能小模块，能够记录出错的代码所在的文件名称和行号。
处理:
1.记录最多8条错误记录，对相同的错误记录(即文件名称和行号完全匹配)只记录一条，错误计数增加；(文件所在的目录不同，文件名和行号相同也要合并)
2.超过16个字符的文件名称，只记录文件的最后有效16个字符；(如果文件名不同，而只是文件名的后16个字符和行号相同，也不要合并)
3.输入的文件可能带路径，记录文件名称不能带路径
输入描述:

一行或多行字符串。每行包括带路径文件名称，行号，以空格隔开。

    文件路径为windows格式

    如：E:\V1R2\product\fpgadrive.c 1325


输出描述:

将所有的记录统计并将结果输出，格式：文件名代码行数数目，一个空格隔开，如: fpgadrive.c 1325 1 

    结果根据数目从多到少排序，数目相同的情况下，按照输入第一次出现顺序排序。

    如果超过8条记录，则只输出前8条记录.

    如果文件名的长度超过16个字符，则只输出后16个字符


输入例子1:

E:\V1R2\product\fpgadrive.c 1325


输出例子1:

fpgadrive.c 1325 1
*/


#include <iostream>
#include <string>
#include <vector>
using namespace std;
struct ErrordRecord {
	string path;
	int lineNumber;
	string fileName;
	int count;
};
int main()
{
	vector<ErrordRecord*> errorRecords;
	string path;
	int lineNumber;
	string fileName;
	while(cin>>path>>lineNumber)
	{
		fileName = path.substr(path.find_last_of("\\")+1);
		bool exist = false;
		for(int i=0;i<errorRecords.size();i++)
		{
			if(errorRecords[i]->fileName == fileName && lineNumber == errorRecords[i]->lineNumber)
			{	
				errorRecords[i]->count = errorRecords[i]->count+1; //注意这边
				exist = true;
				break;
			}
		}
		if(!exist)
		{
			if(fileName.size()>16)
			{
				fileName = fileName.substr(0,16);
			}
			ErrordRecord *errorRecord = new ErrordRecord();
			errorRecord->path = path;
			errorRecord->lineNumber = lineNumber;
			errorRecord->count = 1;
			errorRecord->fileName = fileName;
			errorRecords.push_back(errorRecord);
		}
	}
	
	//sort 
	for(int i=0;i<errorRecords.size();i++)
	{
		for(int j=i+1;j<errorRecords.size();j++)
		{
			if(errorRecords[i]->count < errorRecords[j]->count)
			{
					//这里无法交换对象，只能交换对象的值
					int count = errorRecords[i]->count;
					int lineNumber = errorRecords[i]->lineNumber;
					string fileName = errorRecords[i]->fileName;
					
					errorRecords[i]->count = errorRecords[j]->count;
					errorRecords[i]->lineNumber = errorRecords[j]->lineNumber;
					errorRecords[i]->fileName = errorRecords[j]->fileName;
					
					errorRecords[j]->count = count;
					errorRecords[j]->lineNumber = lineNumber;
					errorRecords[j]->fileName = fileName;
			}
		}
	}

	for(int i=0;i<errorRecords.size() && i<8;i++)
	{
		cout<<errorRecords[i]->fileName<<" "<<errorRecords[i]->lineNumber<<" "<<errorRecords[i]->count<<" ";
	}
}