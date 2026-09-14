import http from 'node:http';
import dgram from 'node:dgram';
import crypto from 'node:crypto';

const networks = new Map();
const sessions = new Map();
const relayEndpoints = new Map();
const relayPort = Number(process.env.RELAY_PORT || 3478);
const httpPort = Number(process.env.PORT || 8080);
const json = (res, status, body) => { res.writeHead(status, {'content-type':'application/json'}); res.end(JSON.stringify(body)); };
const read = req => new Promise((resolve, reject) => { let b=''; req.on('data', c => b += c); req.on('end', () => { try { resolve(b ? JSON.parse(b) : {}) } catch(e) { reject(e) } }); });
const random = bytes => crypto.randomBytes(bytes).toString('hex');
const networkId = () => random(4).toUpperCase();
const passHash = password => crypto.createHash('sha256').update(String(password || '')).digest('hex');
const peers = net => [...net.peers.values()].map(p => ({...p}));

function registerPeer(net, body) {
  const deviceId = String(body.deviceId || random(8));
  const peer = { id: deviceId, name: String(body.name || 'Jogador').slice(0, 40), virtualIp: `10.10.0.${Math.min(250, net.peers.size + 2)}`, pingMs: null, connected: true, transport: 'RELAY', role: 'Membro' };
  const token = random(24); net.peers.set(deviceId, peer); sessions.set(token, { networkId: net.id, deviceId });
  return { token, peer };
}

const server = http.createServer(async (req, res) => {
  try {
    if (req.url === '/health') return json(res, 200, {ok:true, service:'mikaellan-coordinator', relayPort});
    if (req.method === 'POST' && req.url === '/v1/networks') {
      const b = await read(req); const id = networkId(); const net = { id, name: String(b.name || 'MinhaRede').slice(0, 40), secretHash: passHash(b.password), peers: new Map() }; networks.set(id, net);
      const owner = registerPeer(net, { name: b.ownerName || 'Criador', deviceId: b.deviceId });
      return json(res, 201, { networkId: id, name: net.name, token: owner.token, virtualIp: owner.peer.virtualIp, relay: { host: process.env.RELAY_HOST || req.headers.host?.split(':')[0] || '127.0.0.1', port: relayPort } });
    }
    const match = req.url.match(/^\/v1\/networks\/([^/]+)\/(join|peers|leave)$/);
    if (match) {
      const net = networks.get(match[1]) || [...networks.values()].find(candidate => candidate.name.toLowerCase() === decodeURIComponent(match[1]).toLowerCase()); if (!net) return json(res, 404, {error:'network_not_found'});
      if (req.method === 'POST' && match[2] === 'join') {
        const b = await read(req); if (passHash(b.password) !== net.secretHash) return json(res, 401, {error:'invalid_network_code'});
        const result = registerPeer(net, b);
        return json(res, 200, {networkId: net.id, name: net.name, secretHint:'••••••••', token: result.token, virtualIp: result.peer.virtualIp, peers: peers(net), relay:{host:process.env.RELAY_HOST || req.headers.host?.split(':')[0] || '127.0.0.1', port:relayPort}, connected:true});
      }
      if (req.method === 'GET' && match[2] === 'peers') {
        const auth = String(req.headers.authorization || '').replace(/^Bearer\s+/i, ''); if (!sessions.has(auth) || sessions.get(auth).networkId !== net.id) return json(res, 401, {error:'unauthorized'});
        return json(res, 200, {id:net.id, name:net.name, secretHint:'••••••••', peers:peers(net), connected:true});
      }
      if (req.method === 'POST' && match[2] === 'leave') {
        const b = await read(req); const session = sessions.get(b.token); if (session?.networkId === net.id) { net.peers.delete(session.deviceId); sessions.delete(b.token); }
        return json(res, 200, {ok:true});
      }
    }
    json(res, 404, {error:'not_found'});
  } catch { json(res, 400, {error:'bad_request'}); }
});

const relay = dgram.createSocket('udp4');
relay.on('message', (message, remote) => {
  // Frame: MKL1 + uint16 token length + token + uint16 device length + device + raw TUN payload.
  if (message.length < 6 || message.subarray(0, 4).toString() !== 'MKL1') return;
  let offset = 4; const tokenLen = message.readUInt16BE(offset); offset += 2;
  if (offset + tokenLen + 2 > message.length) return; const token = message.subarray(offset, offset + tokenLen).toString(); offset += tokenLen;
  const deviceLen = message.readUInt16BE(offset); offset += 2; if (offset + deviceLen > message.length) return;
  const device = message.subarray(offset, offset + deviceLen).toString(); offset += deviceLen;
  const session = sessions.get(token); if (!session || session.deviceId !== device) return;
  const net = networks.get(session.networkId); if (!net) return;
  relayEndpoints.set(`${session.networkId}:${device}`, { address: remote.address, port: remote.port });
  for (const [peerId] of net.peers) if (peerId !== device) {
    const endpoint = relayEndpoints.get(`${session.networkId}:${peerId}`);
    if (endpoint) relay.send(message, endpoint.port, endpoint.address);
  }
});
relay.bind(relayPort, '0.0.0.0');
server.listen(httpPort, '0.0.0.0', () => console.log(`MikaelLAN coordinator HTTP ${httpPort}; UDP relay ${relayPort}`));
