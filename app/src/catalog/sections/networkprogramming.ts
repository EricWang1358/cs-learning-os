import type { CatalogSection } from '../types'
import { nodeLink, trackLink } from './helpers'

export const networkProgrammingSection: CatalogSection = {
  id: 'networkprogramming',
  number: '04',
  title: 'Networking and distributed systems',
  eyebrow: 'NETWORKING',
  overview: 'A broad networking shelf grouped by protocol layer and distributed-systems concern.',
  guide: 'Follow the TCP/IP fundamentals and network-layer tracks before choosing routing, storage, or distributed systems.',
  summary: 'Keep protocol mechanics, system architecture, and application behavior as separate reading lanes.',
  links: [
    trackLink('networkprogramming', 'tcp-ip-fundamentals', 'TCP/IP fundamentals', 'TCP, UDP, connection lifecycle, flow control, and timeout reasoning.'),
    trackLink('networkprogramming', 'network-fundamentals', 'Network fundamentals', 'Link-layer MAC behavior and shared-network foundations.'),
    trackLink('networkprogramming', 'network-layer', 'Network layer', 'IP addressing, forwarding, ARP, ICMP, and routing algorithms.'),
    trackLink('networkprogramming', 'ip-configuration', 'IP configuration', 'DHCP and the mechanics of joining a network.'),
    trackLink('networkprogramming', 'ip-routing', 'IP routing', 'Longest-prefix matching and forwarding-table decisions.'),
    trackLink('networkprogramming', 'routing-protocols', 'Routing protocols', 'Protocol properties and BGP/IGP integration.'),
    trackLink('networkprogramming', 'application-layer', 'Application layer', 'HTTP, DNS, CDN, and end-to-end request behavior.'),
    trackLink('networkprogramming', 'socket-and-rpc', 'Sockets and RPC', 'Socket programming and remote-procedure-call abstractions.'),
    trackLink('networkprogramming', 'p2p-systems', 'P2P systems', 'Napster, Gnutella, Chord, and decentralized lookup.'),
    trackLink('networkprogramming', 'distributed-storage', 'Distributed storage', 'HDFS, Dynamo, and fault-tolerance trade-offs.'),
    trackLink('networkprogramming', 'distributed-systems', 'Distributed systems', 'Lamport clocks and causal ordering.'),
    trackLink('networkprogramming', 'distributed-ml', 'Distributed ML', 'Parallel training and communication patterns.'),
    trackLink('networkprogramming', 'parallel-computing', 'Parallel computing', 'Critical paths, speedup, and collective communication.'),
    nodeLink('tcp-connection-lifecycle', 'TCP Connection Lifecycle', 'The SYN, ACK, data, close, and retransmission story.'),
    nodeLink('routing-algorithms', 'Routing Algorithms', 'Compare distance-vector and link-state reasoning.'),
  ],
  defaultOpen: false,
}
