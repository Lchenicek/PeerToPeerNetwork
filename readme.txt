a. Group 36
b. Logan Chenicek lchenicek@ufl.edu 55753507
   Zach Webb zwebb@ufl.edu 44138243
   Beatrice Ndalichako bndalichako@ufl.edu 5231****
c.
Logan Chenicek - Bitfield class, File Manager, Message Class, Preferred Neighbors, Optimistic Unchoking, Requests, Piece, Choke and Unchoke messages
Zach Webb - Logger, PeerProcess construction, peerConnection, peerConnection Send/Recieve, Have messages, Threading, Start remote peers
Beatrice - Send/Recieve handshakes, Send/Recieve bitfield, processing peer's bitfield.
d. https://youtu.be/0Czd4rdmCqk
e. We achieved all that we wanted with this project. We successfully got each peer to be able to communicate with each other, the request and send pieces when correct, can choke and unchoke neighbors and properly decide preferred and optimisticly unchoked neighbors.
f. 
g. Overview of project:
When a peer is created, all the data from the config file is read into attributes of the class.
Each peer creates two peer connection threads for each neighbor. When we create the peer, we send handshakes and bitfields so we can determine if we are interested in the peers data and then send an interest message accordingly.
When we start the program we recalculate neighbors and send chokes and unchokes based on that and then selects the optimistically unchoked neighbors. Then we begin the timers to do this again in it's own thread.
The preferred neighbor selection works by creating an ordered list of the top n downloads where n is the number of preferred neighbors. Then we send an unchoke message to the ones selected and choke the others.
The optimistically unchoked peer is selected by randomly selecting a peer from the interested peers that aren't our neighbor. Then we choke the old peer if it isn't now a neighbor and unchoke the new peer.
We will then request pieces whenever we recieve an unchoke message and don't currently have an outstanding request and will request one that we don't have an outstanding request for and are interested in from this peer.
Whenever a peer recieves a request, it will only recieve it from an unchoked peer based on the code structure. So therefore, we don't need to do any checks and can respond with the piece.
Whenver we recieve a piece we will then process it by adding it to our bitfield, adding it to our file manager and recalculating what pieces we want from this peer. If we don't want anything, we will send a not interested message.
We also send out have messages whenever we recieve a piece. Whenver a have message is recieved, we update our bitfield for that respective peer accordingly.
The original peer checks it's peers bitfields for completion and when all of them are complete, it will send out a shutdown message to all peers so that they know to close. This only happens after all peers have started.