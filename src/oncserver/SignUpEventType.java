package oncserver;

public enum SignUpEventType
{
	SIGNUP_IMPORT,					//a list of sign ups was imported
	REPORT,							//one of the sign ups from the list was imported
	UPDATED_ACTIVITIES,				//distribution of the updated activities list
	NEW_ACTIVITIES,					//distribution of the new activities list
	UPDATED_VOLUNTEERS,				//distribution of the updated volunteers list
	NEW_VOLUNTEERS,					//distribution of the new volunteers list
	UPDATED_VOLUNTEER_ACTIVITIES,	//distribution of the updated volunteer activities
	NEW_VOLUNTEER_ACTIVIITES;		//distribution of the new volunteer activities
}
