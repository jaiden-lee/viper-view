## Inspiration
We drew inspiration from Anduril’s Eagle Eye, a lightweight AR headset that overlays enhanced vision, spatial awareness, mission objectives, location data, and friend-or-foe tracking onto the real world. While designed for defense use, it showed us how powerful AR can be when it fuses perception with context. Viper View takes that idea into civilian, low-cost hardware: we pair mobile AR with on-device AI to visualize people and space in real time and to capture rich, world-anchored data. Our goal is to teach models the nuances of close-range human interaction and spatial behavior using everyday devices.
## What it does
Viper View turns a phone in a VR headset into a live spatial capture tool. The app streams camera video while on-device AI adds pose, segmentation, object and even thermal detection overlays. Wearers give quick feedback via gestures and voice. The system uploads synchronized clips and labels to a server, producing world-anchored, real-world data. The result is a low-cost pipeline that teaches models how people move, interact, and use space at scale. 
## How we built it
Phones already ship with solid sensors, AR tracking, and capable mobile GPUs. A ten dollar cardboard headset turns them into hands-free spatial scanners. We built an Android app in Android Studio that runs YOLO models on device for pose, segmentation, and object detection, overlaying results in a dual-screen VR view for real-time feedback while the headset is worn. When available, we also captured thermal data using a FLIR camera. For heavier workloads we tethered the phone over USB to a nearby laptop for additional compute. We integrated voice commands using Android’s native speech-to-text API. All synchronized video, labels, and metadata are captured and streamed to a remote server over Tailscale.

## Challenges we ran into
* Lag issues from running models on-device. 
* Flir Thermal Camera connector issue.
* USB tethering issues.


## Accomplishments that we're proud of
* Built a working end to end demo with simple, low cost supplies.
* Ran pose and segmentation on device with smooth VR rendering.
* Integrated voice control for hands free operation.
* Overcame hardware and SDK roadblocks to ship a live demo.

## What we learned
* Optimizing image models to run on mobile devices.
* Communicating over UDP/TCP connections, tradeoffs between efficency and data integrity.
* How to properly render dual-screen VR with Cardboard while keeping low latency.
* Building human in the loop feedback with voice commands.

## What's next for Viper-View
* Continue refining the product through iterative development
* Fully figure out thermal camera support
* Support more unique and complex data modalities 
* Deploy on the Appstore for users to try. 


