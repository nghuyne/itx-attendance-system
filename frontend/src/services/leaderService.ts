import api from './api';
import type { TeamRosterItemDto } from '../types/api';

export const leaderService = {
  getTeamRoster: (date: string): Promise<TeamRosterItemDto[]> =>
    api.get<TeamRosterItemDto[]>('/leader/team-roster', { params: { date } }).then(r => r.data),
};
