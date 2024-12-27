### Image API
random image for https://waifu.pics/ 

### API Endpoints
- `GET /` - Returns welcome message
- `GET /:type/:endpoint` - Get image by type and endpoint
- `GET /:type?eps=ep1,ep2,ep3` - Get random image from specified endpoints
- `GET /:type/random?ignore=ep1,ep2,ep3` - Get random image excluding specified endpoints

### Example
- GET http://localhost:8888/sfw/waifu
- GET http://localhost:8888/nsfw/waifu
- GET http://localhost:8888/sfw/random?ignore=smile,neko
