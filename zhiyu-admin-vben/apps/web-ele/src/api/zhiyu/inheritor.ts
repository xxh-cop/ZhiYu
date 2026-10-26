import { requestClient } from '#/api/request';

export namespace InheritorApi {
  export interface ProjectListParams {
    page?: number;
    pageSize?: number;
    status?: number;
  }
  export interface ProjectCreateBody {
    title: string;
    intro?: string;
    cultureBg?: string;
    level: number;
    estMinutes?: number;
    category?: string;
    therapyTags?: string;
    coverUrl?: string;
    videoUrl?: string;
  }
  export interface StepBody {
    stepNo: number;
    title: string;
    guide?: string;
    audioUrl?: string;
  }
  export interface MaterialBody {
    name: string;
    qty?: string;
  }
  export interface ProjectListItem {
    id: number | string;
    title: string;
    coverUrl?: string;
    videoUrl?: string;
    category?: string;
    status: number;
    createTime: string;
  }
  export interface StepItem {
    id: number | string;
    stepNo: number;
    title: string;
    guide?: string;
    audioUrl?: string;
  }
  export interface MaterialItem {
    id: number | string;
    name: string;
    qty?: string;
  }
  export interface ProjectDetail extends ProjectListItem {
    intro?: string;
    cultureBg?: string;
    level: number;
    estMinutes?: number;
    therapyTags?: string;
    steps: StepItem[];
    materials: MaterialItem[];
  }
}

export async function getMyProjects(params: InheritorApi.ProjectListParams) {
  const res = await requestClient.get<{
    total: number;
    pageNum: number;
    pageSize: number;
    records: InheritorApi.ProjectListItem[];
  }>('/inheritor/projects', { params });
  return { ...res, total: Number(res.total) };
}

export async function getProjectDetail(id: number | string) {
  return requestClient.get<InheritorApi.ProjectDetail>(
    `/inheritor/projects/${id}`,
  );
}

export async function createProject(data: InheritorApi.ProjectCreateBody) {
  return requestClient.post<{ id: number | string }>('/inheritor/projects', data);
}

export async function updateProject(
  id: number | string,
  data: InheritorApi.ProjectCreateBody,
) {
  return requestClient.put(`/inheritor/projects/${id}`, data);
}

export async function deleteProject(id: number | string) {
  return requestClient.delete(`/inheritor/projects/${id}`);
}

export async function addStep(
  projectId: number | string,
  data: InheritorApi.StepBody,
) {
  return requestClient.post<{ id: number | string }>(
    `/inheritor/projects/${projectId}/steps`,
    data,
  );
}

export async function updateStep(
  projectId: number | string,
  stepId: number | string,
  data: InheritorApi.StepBody,
) {
  return requestClient.put(
    `/inheritor/projects/${projectId}/steps/${stepId}`,
    data,
  );
}

export async function deleteStep(
  projectId: number | string,
  stepId: number | string,
) {
  return requestClient.delete(
    `/inheritor/projects/${projectId}/steps/${stepId}`,
  );
}

export async function addMaterial(
  projectId: number | string,
  data: InheritorApi.MaterialBody,
) {
  return requestClient.post<{ id: number | string }>(
    `/inheritor/projects/${projectId}/materials`,
    data,
  );
}

export async function deleteMaterial(
  projectId: number | string,
  materialId: number | string,
) {
  return requestClient.delete(
    `/inheritor/projects/${projectId}/materials/${materialId}`,
  );
}

// ── 传承人资料（批次 6-A） ──

export interface InheritorProfileSelf {
  id: number | string;
  userId: number | string;
  skill: string;
  intro: string;
  honor: string;
  verified: number;
  avatarUrl: string;
}

export async function getMyInheritorProfile() {
  return requestClient.get<InheritorProfileSelf>('/inheritor/profile');
}

export async function updateMyInheritorProfile(data: {
  skill?: string;
  intro?: string;
  honor?: string;
  avatarUrl?: string;
}) {
  return requestClient.put<InheritorProfileSelf>('/inheritor/profile', data);
}
