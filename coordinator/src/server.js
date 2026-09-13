import http from 'node:http';
import crypto from 'node:crypto';

const networks = new Map();
const json = (res, status, body) => { res.writeHead(status, {'content-type':'application/json'}); res.end(JSON.stringify(body)); };
const read = req => new Promise((resolve, reject) => { let b=''; req.on('data', c => b += c); req.on('end', () => { try { resolve(b ? JSON.parse(b) : {}) } catch(e) { reject(e) } }); });
const id = () => crypto.randomBytes(4).toString('hex').toUpperCase();
function peers(net) { return [...net.peers.values()].map(p => ({...p})); }
const server = http.createServer(async (req, res) => {
  try {
    if (req.url === '/health') return json(res, 200, {ok:true, service:'mikaellan-coordinator'});
    if (req.method === 'POST' && req.url === '/v1/networks') { const b=await read(req); const networkId=id(); const token=crypto.randomBytes(24).toString('hex'); const net={id:networkId,name:b.name||'MinhaRede',secretHash:crypto.createHash('sha256').update(b.password||'').digest('hex'),peers:new Map()}; networks.set(networkId,net); return json(res,201,{networkId,token}); }
    const match = req.url.match(/^\/v1\/networks\/([^/]+)\/(join|peers)$/); if (match) { const net=networks.get(match[1]); if(!net) return json(res,404,{error:'network_not_found'}); if(req.method==='POST' && match[2]==='join') { const b=await read(req); const hash=crypto.createHash('sha256').update(b.password||'').digest('hex'); if(hash!==net.secretHash) return json(res,401,{error:'invalid_network_code'}); const virtualIp=`10.10.0.${Math.min(250,net.peers.size+2)}`; const peer={id:b.deviceId||id(),name:b.name||'Jogador',virtualIp,pingMs:null,connected:true,transport:'P2P'}; net.peers.set(peer.id,peer); return json(res,200,{networkId:net.id,name:net.name,secretHint:'••••••••',virtualIp,peers:peers(net),connected:true}); } if(req.method==='GET' && match[2]==='peers') return json(res,200,{id:net.id,name:net.name,secretHint:'••••••••',virtualIp:'10.10.0.2',peers:peers(net),connected:true}); }
    json(res,404,{error:'not_found'});
  } catch(e) { json(res,400,{error:'bad_request'}); }
});
server.listen(process.env.PORT||8080, '0.0.0.0', () => console.log(`MikaelLAN coordinator listening on ${process.env.PORT||8080}`));
