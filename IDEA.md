# Queue (very demanding for something simpler)

- A longgg lived FG service will always run in background
- It must expose method to:
    - enqueue -> enqueue a specific upload request to the internal Queue
    - status_check -> check status of specific upload given its id (output of queue)
- Internally it writes to a SQL database the three main status:
    - QUEUED / IDLE -> it's starting (or will be starting soon)
    - RUNNING -> it's working (uploading...), in this state using status_check must give done,
      total, elapsed (it could be calculated on the fly by System.currentTimeMillis () -
      item.queuedAt)
    - FINISHED / FAILED -> it's finished working and is completed (FINISHED) or failed (FAILED),
      this state could contain an extra field for reason?
- Writing to SQL database makes it easy for any UI part to list all entities, and specifically look
  for one
- But..... this is by design sequential, which could be an issue given, say we wanna upload 4 3Gib
  files, we will have to wait 1 to finish...
- Maybe, up2k?

# Work (This fits nicely...)

- Same as above, but the queueing is managed by android, the work execution is also managed by
  android
- It can run multiple requests, without desync state, though in above desync can rarely happen?
  because upload in parallel is handled by server, not our concern????
- State can also be notified by both methods
- Here, we are only tasked to maintain a state of our work only!!. in comparison to above, we have
  to manage a lot of things...
- we can add conditions

# NOTES

- for multiple files, what should be done?, separate upload request or one request with multiple
  files?
- Basic notification for one upload request:

## short_notification

finest_flower_in_gardeeen.mov 43% +++++++++++++++++---------------------

## expanded_notification

finest_flower_in_gardeeen.mov 43% elapsed time:                  1.4 min bytes send:
438300943029292 bytes left:            240942000932000 bytes total:
679243943961292 +++++++++++++++++---------------------

[Pause]                       [Cancel]

- clicking on any notification above should fire this activity:

## info screen

        finest_flower_in_gardeeen.mov
    elapsed time:                  1.4 min
    bytes send:            438300943029292
    bytes left:            240942000932000
    bytes total:           679243943961292
    started at:               27 nov, 2020
    
    [===============_____________________]

# Data

UI needs to know:

- done -> the bytes send to server and acknowledged
- total -> the bytes sub-total
- started_when -> started when, can be used to calculate elapsed time
- status_text -> text for current status, for example when finished it will contain the server
  response, same when failed
